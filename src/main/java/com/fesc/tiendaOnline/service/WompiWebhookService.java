package com.fesc.tiendaOnline.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fesc.tiendaOnline.config.WompiConfig;
import com.fesc.tiendaOnline.exception.UnauthorizedException;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.repository.CompraRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;

@Service
public class WompiWebhookService {

    private final WompiConfig wompiConfig;
    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Boolean> processedEvents = new ConcurrentHashMap<>();

    public WompiWebhookService(WompiConfig wompiConfig,
                                CompraRepository compraRepository,
                                ProductoRepository productoRepository,
                                ObjectMapper objectMapper) {
        this.wompiConfig = wompiConfig;
        this.compraRepository = compraRepository;
        this.productoRepository = productoRepository;
        this.objectMapper = objectMapper;
    }

    public void procesarEvento(String payload, String checksum) {

        try {
            // 1. Parsear el payload
            JsonNode root = objectMapper.readTree(payload);

            // 2. Extraer datos de la transacción
            JsonNode transaction = root.path("data").path("transaction");
            String wompiTransaccionId = transaction.get("id").asString();
            String status = transaction.get("status").asString();

            // 3. Verificar firma según documentación oficial de Wompi:
            //    SHA256( valores de signature.properties + timestamp + eventsKey )
            //    Los properties apuntan a campos dentro del objeto "data"
            JsonNode signatureNode = root.path("signature");
            long timestamp = root.get("timestamp").longValue();
            JsonNode propertiesNode = signatureNode.path("properties");

            StringBuilder concatenado = new StringBuilder();
            for (JsonNode prop : propertiesNode) {
                // Cada property es un path como "transaction.id", "transaction.status", etc.
                String propPath = prop.asString();
                String valor = resolverValorPropiedad(root.path("data"), propPath);
                concatenado.append(valor);
            }
            concatenado.append(timestamp);
            concatenado.append(wompiConfig.getEventsKey());

            String firmaCalculada = calcularFirma(concatenado.toString());
            if (!firmaCalculada.equalsIgnoreCase(checksum)) {
                throw new UnauthorizedException("Firma del webhook Wompi inválida");
            }

            // 4. Extraer el id del evento para idempotencia
            //    Usamos la combinación transaccionId+status como clave idempotente
            //    ya que el payload no tiene un campo "event id" separado
            String claveIdempotencia = wompiTransaccionId + "_" + status;

            // 5. Verificar idempotencia
            if (processedEvents.containsKey(claveIdempotencia)) {
                return; // evento ya procesado
            }

            // 6. Procesar según status
            switch (status) {
                case "APPROVED" -> procesarAprobado(wompiTransaccionId);
                case "DECLINED", "VOIDED" -> procesarCancelado(wompiTransaccionId);
                default -> { /* status desconocido o intermedio — ignorar */ }
            }

            // 7. Marcar evento como procesado
            processedEvents.put(claveIdempotencia, Boolean.TRUE);

        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Error al procesar webhook Wompi: " + ex.getMessage(), ex);
        }
    }

    /**
     * Resuelve el valor de un campo navegando por el árbol JSON usando un path
     * con notación de puntos (ej: "transaction.id" -> data.transaction.id).
     */
    private String resolverValorPropiedad(JsonNode dataNode, String propPath) {
        // El path en "properties" ya incluye el prefijo del objeto raíz dentro de "data"
        // ej: "transaction.id" -> navegamos data -> transaction -> id
        String[] partes = propPath.split("\\.");
        JsonNode nodo = dataNode;
        for (String parte : partes) {
            nodo = nodo.path(parte);
        }
        return nodo.asString();
    }

    // PROCESAMIENTO DE ESTADOS DE TRANSACCION EN WOMPI

    // 1. ESTADO APROBADO
    @Transactional
    protected void procesarAprobado(String wompiTransaccionId) {
        CompraEntity compra = compraRepository.findByWompiTransaccionId(wompiTransaccionId)
                .orElse(null);
        if (compra == null) {
            return; // compra no encontrada — ignorar
        }
        compra.setCompraEstado(CompraEstado.ACEPTADO);
        compraRepository.save(compra);
    }

    // 2. ESTADO CANCELADO
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    protected void procesarCancelado(String wompiTransaccionId) {
        CompraEntity compra = compraRepository.findByWompiTransaccionId(wompiTransaccionId)
                .orElse(null);
        if (compra == null) {
            return;
        }

        // Restaurar stock con bloqueo pesimista
        if (compra.getDetalles() != null) {
            compra.getDetalles().forEach(detalle -> {
                productoRepository.findByIdWithLock(detalle.getProducto().getIdProducto())
                        .ifPresent(producto -> {
                            producto.setStockProducto(producto.getStockProducto() + detalle.getCantidad());
                            productoRepository.save(producto);
                        });
            });
        }

        compra.setCompraEstado(CompraEstado.CANCELADO);
        compraRepository.save(compra);
    }

    // HELPERS
    private String calcularFirma(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
