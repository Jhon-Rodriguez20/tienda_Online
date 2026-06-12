package com.fesc.tiendaOnline.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.model.dto.ActualizarEstadoCompraDTO;
import com.fesc.tiendaOnline.model.dto.CompraBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.CompraMetodoPagoResponseDTO;
import com.fesc.tiendaOnline.model.dto.CompraRequestDTO;
import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.fesc.tiendaOnline.model.dto.IdempotencyResult;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.WompiTransaccionRequestDTO;
import com.fesc.tiendaOnline.model.dto.WompiTransaccionResponseDTO;
import com.fesc.tiendaOnline.model.entity.CompraDetalleEntity;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.CompraEstado;
import com.fesc.tiendaOnline.model.entity.MetodoPagoCompraEntity;
import com.fesc.tiendaOnline.model.entity.ProductoEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;
import com.fesc.tiendaOnline.repository.CompraDetalleRepository;
import com.fesc.tiendaOnline.repository.CompraRepository;
import com.fesc.tiendaOnline.repository.MetodoPagoRepository;
import com.fesc.tiendaOnline.repository.ProductoRepository;
import com.fesc.tiendaOnline.repository.UsuarioRepository;
import com.fesc.tiendaOnline.exception.WompiTimeoutException;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final NumeroCompraGenerator numeroCompraGenerator;
    private final AdminValidationService adminValidationService;
    private final UsuarioValidationService usuarioValidationService;
    private final IdempotencyStore idempotencyStore;
    private final WompiService wompiService;
    
    public CompraService(CompraRepository compraRepository, CompraDetalleRepository compraDetalleRepository,
            ProductoRepository productoRepository, UsuarioRepository usuarioRepository,
            MetodoPagoRepository metodoPagoRepository, NumeroCompraGenerator numeroCompraGenerator,
            AdminValidationService adminValidationService, UsuarioValidationService usuarioValidationService,
            IdempotencyStore idempotencyStore, WompiService wompiService) {
        
        this.compraRepository = compraRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.numeroCompraGenerator = numeroCompraGenerator;
        this.adminValidationService = adminValidationService;
        this.usuarioValidationService = usuarioValidationService;
        this.idempotencyStore = idempotencyStore;
        this.wompiService = wompiService;
    }

    // OBTENER TODOS LOS METODOS DE PAGO PARA COMPRA
    @Transactional(readOnly = true)
    public List<CompraMetodoPagoResponseDTO> getMetodoPago() {
        return metodoPagoRepository.findAll().stream()
            .map(this::metodoPagoConvertirAResponseDTO)
            .collect(Collectors.toList());
    }

    // CREAR COMPRA - SOLO CLIENTES
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public IdempotencyResult<CompraResponseDTO> realizarCompra(CompraRequestDTO request, UUID usuarioId, String idempotencyKey) {
        // Idempotency check: retornar respuesta cacheada si la clave ya existe
        Optional<CompraResponseDTO> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent()) {
            return new IdempotencyResult<>(cached.get(), true);
        }

        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado"));
        
        MetodoPagoCompraEntity metodoPago = metodoPagoRepository.findById(request.getIdMetodoPago())
                .orElseThrow(() -> new BusinessRuleException("Método de pago no encontrado"));
        
        double totalPagado = 0.0;
        
        // Generar número de compra único
        String numeroCompra;
        do {
            numeroCompra = numeroCompraGenerator.generarNumeroCompra();
        } while (compraRepository.existsByNumeroCompra(numeroCompra));
        
        // Crear compra
        CompraEntity compra = new CompraEntity();
        compra.setNumeroCompra(numeroCompra);
        compra.setTotalPagado(totalPagado);
        compra.setFechaCompra(LocalDateTime.now());
        compra.setCompraEstado(CompraEstado.PENDIENTE);
        compra.setIdMetodoPago(metodoPago);
        compra.setUsuario(usuario);
        
        // Acumular productos modificados para saveAll post-loop
        Map<UUID, ProductoEntity> modifiedProducts = new LinkedHashMap<>();

        // Procesar items y crear detalles
        for (CompraRequestDTO.ItemCompraDTO item : request.getItems()) {
            // Bloqueo pesimista para prevenir condiciones de carrera (Requirement 5.1)
            ProductoEntity producto = productoRepository.findByIdWithLock(item.getIdProducto())
                    .orElseThrow(() -> new BusinessRuleException("Producto no encontrado: " + item.getIdProducto()));
            
            if (producto.getStockProducto() < item.getCantidad()) {
                throw new BusinessRuleException("Stock insuficiente para el producto: " + producto.getNombreProducto());
            }
            
            CompraDetalleEntity detalle = new CompraDetalleEntity();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecioProducto());
            detalle.setSubtotal(producto.getPrecioProducto() * item.getCantidad());
            
            compra.addDetalle(detalle);
            
            totalPagado += detalle.getSubtotal();
            
            producto.setStockProducto(producto.getStockProducto() - item.getCantidad());
            modifiedProducts.put(producto.getIdProducto(), producto);
        }
        
        // Guardar todos los productos modificados en una sola operación
        productoRepository.saveAll(modifiedProducts.values());
        
        compra.setTotalPagado(totalPagado);
        
        // Guardar la compra una sola vez; CascadeType.ALL persiste los detalles
        CompraEntity compraGuardada = compraRepository.save(compra);

        // Llamar a Wompi tras guardar la CompraEntity
        // Capturar WompiTimeoutException y lanzar BusinessRuleException para rollback
        if (request.getWompiTipoPago() != null && !request.getWompiTipoPago().isBlank()) {

            try {
                long amountInCents = (long)(totalPagado * 100);
                String currency = "COP";

                // Construir signature
                String signature =
                wompiService.calcularFirmaIntegridad(
                        numeroCompra,
                        amountInCents,
                        currency
                );

                // Construir payment_method según wompiTipoPago
                WompiTransaccionRequestDTO.PaymentMethod paymentMethod = new WompiTransaccionRequestDTO.PaymentMethod();
                
                switch (request.getWompiTipoPago()) {
                    
                    case "BANCOLOMBIA_TRANSFER" -> {
                        paymentMethod.setType("BANCOLOMBIA_TRANSFER");
                        // Campos obligatorios para BANCOLOMBIA_TRANSFER según docs oficiales
                        paymentMethod.setUser_type("PERSON");
                        paymentMethod.setPayment_description("Pago en tienda online");
                    }
                    case "NEQUI" -> {
                        paymentMethod.setType("NEQUI");
                        paymentMethod.setPhone_number(request.getWompiNequiPhone());
                    }
                    case "CARD" -> {
                        paymentMethod.setType("CARD");
                        paymentMethod.setToken(request.getWompiCardToken());
                        paymentMethod.setInstallments(request.getCuotas() != null ? request.getCuotas() : 1);
                    }
                    default -> throw new BusinessRuleException("Tipo de pago Wompi no soportado: " + request.getWompiTipoPago());
                }

                // Construir el request DTO completo
                // signature es un string plano con el hash SHA-256 (no objeto)
                WompiTransaccionRequestDTO wompiRequest = new WompiTransaccionRequestDTO();
                wompiRequest.setAmount_in_cents(amountInCents);
                wompiRequest.setCurrency(currency);
                wompiRequest.setReference(numeroCompra);
                wompiRequest.setCustomer_email(usuario.getEmail());
                wompiRequest.setSignature(signature);
                wompiRequest.setPayment_method_type(request.getWompiTipoPago());
                wompiRequest.setPayment_method(paymentMethod);

                // Llamar a la API de Wompi
                String acceptanceToken = wompiService.obtenerAcceptanceToken();

                wompiRequest.setAcceptanceToken(acceptanceToken);
                WompiTransaccionResponseDTO wompiResponse = wompiService.crearTransaccion(wompiRequest);

                // Manejar el status de la respuesta de Wompi
                String wompiStatus = wompiResponse.getStatus();

                if ("APPROVED".equals(wompiStatus)) {
                    compraGuardada.setCompraEstado(CompraEstado.ACEPTADO);
                    compraGuardada.setWompiTransaccionId(wompiResponse.getId());
                
                } else if ("PENDING".equals(wompiStatus)) {
                    compraGuardada.setCompraEstado(CompraEstado.PENDIENTE);
                    compraGuardada.setWompiTransaccionId(wompiResponse.getId());
                
                } else {
                    throw new BusinessRuleException("Pago rechazado: " + wompiStatus);
                }

                // Guardar la CompraEntity actualizada con el estado y wompiTransaccionId
                compraGuardada = compraRepository.save(compraGuardada);

                CompraResponseDTO response = convertirAResponseDTO(compraGuardada);
                // Incluir async_payment_url en la respuesta para pagos PENDING
                if ("PENDING".equals(wompiStatus)) {
                    response.setAsyncPaymentUrl(wompiResponse.getAsync_payment_url());
                }

                // Almacenar en el store de idempotencia para futuras solicitudes duplicadas
                idempotencyStore.put(idempotencyKey, response);

                return new IdempotencyResult<>(response, false);

            } catch (WompiTimeoutException ex) {
                throw new BusinessRuleException("No se pudo procesar el pago: timeout de conexión con Wompi");
            }
        }

        CompraResponseDTO response = convertirAResponseDTO(compraGuardada);

        // Almacenar en el store de idempotencia para futuras solicitudes duplicadas
        idempotencyStore.put(idempotencyKey, response);

        return new IdempotencyResult<>(response, false);
    }

    // OBTENER MIS COMPRAS - CLIENTES
    @Transactional(readOnly = true)
    public PaginacionResponseDTO<CompraResponseDTO> getMisCompras(UUID usuarioId, CompraBusquedaDTO busqueda) {
        Pageable pageable = PageRequest.of(busqueda.getPagina(), busqueda.getTamanio());
        Page<CompraEntity> paginaCompras;
        
        if (busqueda.getNumeroCompra() != null && !busqueda.getNumeroCompra().isEmpty()) {
            Optional<CompraEntity> compra = compraRepository.findByUsuarioIdAndNumeroCompra(usuarioId, busqueda.getNumeroCompra());
            paginaCompras = compra
                .<Page<CompraEntity>>map(c -> new PageImpl<>(List.of(c), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
        
            } else if (busqueda.getFechaInicio() != null && busqueda.getFechaFin() != null) {
            paginaCompras = compraRepository.findByUsuarioIdAndFechaBetween(usuarioId,
                busqueda.getFechaInicio(), busqueda.getFechaFin(), pageable);
        
        } else {
            paginaCompras = compraRepository.findByUsuarioId(usuarioId, pageable);
        }
        
        return convertirAPaginacionResponse(paginaCompras);
    }

    // OBTENER DETALLE DE COMPRA POR ID - CLIENTES
    // Requirements: 6.1
    @Transactional(readOnly = true)
    public CompraResponseDTO getCompraById(UUID compraId, UUID usuarioId) {
        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));
        
        UsuarioEntity usuario = usuarioValidationService.obtenerUsuarioPorIdRolAdmin(usuarioId);
        String rolUsuario = usuario.getUsuarioRol().getRolUsuario();
        boolean esAdmin = "ADMIN".equals(rolUsuario);

        // validar que la compra sea del usuario o el administrador intente verla
        if (!compraEntity.getUsuario().getIdUsuario().equals(usuarioId) && !esAdmin) {
            throw new BusinessRuleException("No tienes permisos para ver esta compra");
        }

        return convertirAResponseDTO(compraEntity);
    }

    // CANCELAR COMPRA SI ESTÁ EN PENDIENTE - CLIENTES
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void cancelarCompra(UUID compraId, UUID usuarioId) {
        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));

        if (!compraEntity.getUsuario().getIdUsuario().equals(usuarioId)) {
            throw new BusinessRuleException("No tienes permisos para cancelar esta compra");
        }

        if (compraEntity.getCompraEstado() != CompraEstado.PENDIENTE) {
            throw new BusinessRuleException("Solo se pueden cancelar las compras en estado PENDIENTE");
        }

        // Acumular productos restaurados para saveAll post-loop
        Map<UUID, ProductoEntity> restoredProducts = new LinkedHashMap<>();

        // Restaurar stock de productos con bloqueo pesimista
        for (CompraDetalleEntity detalle : compraEntity.getDetalles()) {
            ProductoEntity productoEntity = productoRepository
                    .findByIdWithLock(detalle.getProducto().getIdProducto())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Producto no encontrado: " + detalle.getProducto().getIdProducto()));
            productoEntity.setStockProducto(productoEntity.getStockProducto() + detalle.getCantidad());
            restoredProducts.put(productoEntity.getIdProducto(), productoEntity);
            // NO se llama productoRepository.save(productoEntity) dentro del loop
        }

        // Guardar todos los productos restaurados en una sola operación
        productoRepository.saveAll(restoredProducts.values());

        compraEntity.setCompraEstado(CompraEstado.CANCELADO);
        compraRepository.save(compraEntity);
    }

    // OBTENER LAS COMPRAS - ADMIN
    @Transactional(readOnly = true)
    public PaginacionResponseDTO<CompraResponseDTO> getAllCompras(CompraBusquedaDTO compraBusquedaDTO, UUID adminId) {
        adminValidationService.validarAdmin(adminId);
        Pageable pageable = PageRequest.of(compraBusquedaDTO.getPagina(), compraBusquedaDTO.getTamanio());
        Page<CompraEntity> paginaCompras;

        if (compraBusquedaDTO.getNumeroCompra() != null && !compraBusquedaDTO.getNumeroCompra().isEmpty()) {
            Optional<CompraEntity> compra = compraRepository.findByNumeroCompra(compraBusquedaDTO.getNumeroCompra());
            paginaCompras = compra
                .<Page<CompraEntity>>map(c -> new PageImpl<>(List.of(c), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
        
        } else if (compraBusquedaDTO.getFechaInicio() != null && compraBusquedaDTO.getFechaFin() != null) {
            paginaCompras = compraRepository.findByFechaBetween(
                compraBusquedaDTO.getFechaInicio(), compraBusquedaDTO.getFechaFin(), pageable);
        
        } else {
            paginaCompras = compraRepository.findAllWithDetails(pageable);
        }

        return convertirAPaginacionResponse(paginaCompras);
    }

    // ACTUALIZAR ESTADO DE COMPRA - ADMIN
    @Transactional
    public IdempotencyResult<CompraResponseDTO> putEstadoCompra(UUID compraId, ActualizarEstadoCompraDTO request, UUID adminId, String idempotencyKey) {
        // Idempotency check: retornar respuesta cacheada si la clave ya existe
        Optional<CompraResponseDTO> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent()) {
            return new IdempotencyResult<>(cached.get(), true);
        }

        adminValidationService.validarAdmin(adminId);

        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));
        
        CompraEstado nuevoEstado;
        try {
            nuevoEstado = CompraEstado.valueOf(request.getEstado().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Estado no válido.");
        }

        // Validaciones del estado
        CompraEstado estadoActual = compraEntity.getCompraEstado();
        if (estadoActual == CompraEstado.CANCELADO) {
            throw new BusinessRuleException("No se puede modificar una compra cancelada");
        }
        if (estadoActual == CompraEstado.ENTREGADO) {
            throw new BusinessRuleException("La compra ya está entregada, no se puede modificar");
        }
        if (estadoActual == CompraEstado.PENDIENTE && nuevoEstado != CompraEstado.ACEPTADO) {
            throw new BusinessRuleException("Desde PENDIENTE solo se puede pasar a ACEPTADO");
        }
        if (estadoActual == CompraEstado.ACEPTADO && nuevoEstado != CompraEstado.ENTREGADO) {
            throw new BusinessRuleException("Desde ACEPTADO solo se puede pasar a ENTREGADO");
        }
        compraEntity.setCompraEstado(nuevoEstado);
        CompraEntity compraActualizada = compraRepository.save(compraEntity);
        
        CompraResponseDTO response = convertirAResponseDTO(compraActualizada);

        // Almacenar en el store de idempotencia para futuras solicitudes duplicadas
        idempotencyStore.put(idempotencyKey, response);

        return new IdempotencyResult<>(response, false);
    }

    private CompraResponseDTO convertirAResponseDTO(CompraEntity compra) {
        CompraResponseDTO response = new CompraResponseDTO();
        response.setIdCompra(compra.getIdCompra());
        response.setNumeroCompra(compra.getNumeroCompra());
        response.setTotalPagado(compra.getTotalPagado());
        response.setFechaCompra(compra.getFechaCompra());
        response.setEstado(compra.getCompraEstado().toString());
        response.setMetodoPago(compra.getIdMetodoPago().getMetodoPago());
        response.setWompiTransaccionId(compra.getWompiTransaccionId());
        
        List<CompraResponseDTO.CompraDetalleResponseDTO> detalles = new ArrayList<>();
        if (compra.getDetalles() != null && !compra.getDetalles().isEmpty()) {
            for (CompraDetalleEntity detalle : compra.getDetalles()) {
                CompraResponseDTO.CompraDetalleResponseDTO detalleDTO = new CompraResponseDTO.CompraDetalleResponseDTO();
                detalleDTO.setIdProducto(detalle.getProducto().getIdProducto());
                detalleDTO.setNombreProducto(detalle.getProducto().getNombreProducto());
                detalleDTO.setCantidad(detalle.getCantidad());
                detalleDTO.setPrecioUnitario(detalle.getPrecioUnitario());
                detalleDTO.setSubtotal(detalle.getSubtotal());
                detalles.add(detalleDTO);
            }
        }
        response.setDetalles(detalles);
        
        return response;
    }

    private CompraMetodoPagoResponseDTO metodoPagoConvertirAResponseDTO(MetodoPagoCompraEntity metodoPagoCompraEntity) {
        CompraMetodoPagoResponseDTO responseDTO = new CompraMetodoPagoResponseDTO();
        responseDTO.setIdMetodoPago(metodoPagoCompraEntity.getIdMetodoPago());
        responseDTO.setMetodoPago(metodoPagoCompraEntity.getMetodoPago());
        return responseDTO;
    }

    private PaginacionResponseDTO<CompraResponseDTO> convertirAPaginacionResponse(Page<CompraEntity> paginaCompras) {
        List<CompraResponseDTO> contenido = paginaCompras.getContent().stream()
                .map(this::convertirAResponseDTO)
                .collect(Collectors.toList());
        
        return new PaginacionResponseDTO<>(
            contenido,
            paginaCompras.getNumber(),
            paginaCompras.getSize(),
            paginaCompras.getTotalElements(),
            paginaCompras.getTotalPages(),
            paginaCompras.isLast(),
            paginaCompras.isFirst()
        );
    }
}
