package com.fesc.tiendaOnline.service;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fesc.tiendaOnline.config.WompiConfig;
import com.fesc.tiendaOnline.exception.NotFoundException;
import com.fesc.tiendaOnline.exception.WompiTimeoutException;
import com.fesc.tiendaOnline.model.dto.WompiMerchantResponseDTO;
import com.fesc.tiendaOnline.model.dto.WompiPagoEstadoResponseDTO;
import com.fesc.tiendaOnline.model.dto.WompiTransaccionRequestDTO;
import com.fesc.tiendaOnline.model.dto.WompiTransaccionResponseDTO;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.repository.CompraRepository;

@Service
public class WompiService {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 15;

    private final WompiConfig wompiConfig;
    private final RestClient restClient;
    private final CompraRepository compraRepository;

    @Autowired
    public WompiService(WompiConfig wompiConfig, CompraRepository compraRepository) {
        this.wompiConfig = wompiConfig;
        this.compraRepository = compraRepository;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS));

        MappingJackson2HttpMessageConverter jackson2Converter = new MappingJackson2HttpMessageConverter();

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c.getClass().getName().contains("Jackson"));
                    converters.add(0, jackson2Converter);
                })
                .build();
    }

    WompiService(WompiConfig wompiConfig, CompraRepository compraRepository, RestClient restClient) {
        this.wompiConfig = wompiConfig;
        this.compraRepository = compraRepository;
        this.restClient = restClient;
    }

    public String obtenerAcceptanceToken() {

    WompiMerchantResponseDTO response =
        restClient.get()
            .uri(wompiConfig.getBaseUrl()
                + "/merchants/"
                + wompiConfig.getPublicKey())
            .retrieve()
            .body(WompiMerchantResponseDTO.class);

        return response.getData()
                .getPresignedAcceptance()
                .getAcceptanceToken();
    }

    // CREAR TRANSACCION EN WOMPI
    public WompiTransaccionResponseDTO crearTransaccion(WompiTransaccionRequestDTO request) {
        try {
            return restClient.post()
                    .uri(wompiConfig.getBaseUrl() + "/transactions")
                    .header("Authorization", "Bearer " + wompiConfig.getPrivateKey())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            (req, res) -> {
                                String body = "";
                                try (InputStream is = res.getBody()) {
                                    body = new String(
                                            is.readAllBytes(),
                                            StandardCharsets.UTF_8
                                    );
                                }
                                throw new BusinessRuleException(
                                        "Error al procesar el pago con Wompi: " + res.getStatusCode() + "Body: " + body);
                            })
                    .body(WompiTransaccionResponseDTO.class);
        
        } catch (ResourceAccessException ex) {
            throw new WompiTimeoutException("Timeout al crear transacción con Wompi", ex);
        }
    }

    // CONSULTAR EL ESTADO DE UNA TRANSACCION EN WOMPI
    public WompiTransaccionResponseDTO consultarTransaccion(String wompiTransaccionId) {
        try {
            return restClient.get()
                    .uri(wompiConfig.getBaseUrl() + "/transactions/" + wompiTransaccionId)
                    .header("Authorization", "Bearer " + wompiConfig.getPrivateKey())
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 404,
                            (req, res) -> {
                                throw new NotFoundException(
                                        "Transacción Wompi no encontrada: " + wompiTransaccionId);
                            })
                    .body(WompiTransaccionResponseDTO.class);
        
        } catch (ResourceAccessException ex) {
            throw new WompiTimeoutException("Timeout al consultar transacción con Wompi", ex);
        }
    }

    // CALCULAR LA FIRMA DE INTEGRIDAD PARA UNA TRANSACCIÓN WOMPI USANDO SHA-256 
    public String calcularFirmaIntegridad(String referencia, long amountInCents, String currency) {
        String data = referencia + amountInCents + currency + wompiConfig.getIntegrityKey();

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
            // SHA-256 siempre debe estar disponible en el JDK
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    // CONSULTAR ESTADO DE UN PAGO EN WOMPI
    public WompiPagoEstadoResponseDTO consultarEstadoPago(UUID compraId, UUID usuarioId) {
        CompraEntity compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new NotFoundException("Compra no encontrada"));

        String wompiTransaccionId = compra.getWompiTransaccionId();
        if (wompiTransaccionId == null || wompiTransaccionId.isBlank()) {
            throw new NotFoundException("No existe una transacción Wompi asociada a esta compra");
        }

        WompiTransaccionResponseDTO wompiResponse = consultarTransaccion(wompiTransaccionId);

        if ("APPROVED".equals(wompiResponse.getStatus())
                && CompraEstado.PENDIENTE == compra.getCompraEstado()) {
            compra.setCompraEstado(CompraEstado.ACEPTADO);
            compraRepository.save(compra);
        }

        WompiPagoEstadoResponseDTO wompiPagoEstadoResponseDTO = new WompiPagoEstadoResponseDTO();
        wompiPagoEstadoResponseDTO.setCompraId(compra.getIdCompra());
        wompiPagoEstadoResponseDTO.setNumeroCompra(compra.getNumeroCompra());
        wompiPagoEstadoResponseDTO.setEstadoCompra(compra.getCompraEstado().name());
        wompiPagoEstadoResponseDTO.setWompiTransaccionId(compra.getWompiTransaccionId());
        wompiPagoEstadoResponseDTO.setEstadoWompi(wompiResponse.getStatus());
        wompiPagoEstadoResponseDTO.setFechaActualizacion(LocalDateTime.now());

        return wompiPagoEstadoResponseDTO;
    }
}
