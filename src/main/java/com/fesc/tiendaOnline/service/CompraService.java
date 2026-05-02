package com.fesc.tiendaOnline.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fesc.tiendaOnline.model.dto.ActualizarEstadoCompraDTO;
import com.fesc.tiendaOnline.model.dto.CompraBusquedaDTO;
import com.fesc.tiendaOnline.model.dto.CompraRequestDTO;
import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
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

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final NumeroCompraGenerator numeroCompraGenerator;
    private final AdminValidationService adminValidationService;
    
    public CompraService(CompraRepository compraRepository, CompraDetalleRepository compraDetalleRepository,
            ProductoRepository productoRepository, UsuarioRepository usuarioRepository,
            MetodoPagoRepository metodoPagoRepository, NumeroCompraGenerator numeroCompraGenerator,
            AdminValidationService adminValidationService) {
        
        this.compraRepository = compraRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.numeroCompraGenerator = numeroCompraGenerator;
        this.adminValidationService = adminValidationService;
    }

    // CREAR COMPRA - SOLO CLIENTES
    @Transactional
    public CompraResponseDTO realizarCompra(CompraRequestDTO request, UUID usuarioId) {     
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
        
        // Procesar items y crear detalles
        for (CompraRequestDTO.ItemCompraDTO item : request.getItems()) {
            ProductoEntity producto = productoRepository.findById(item.getIdProducto())
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
            productoRepository.save(producto);
        }
        
        compra.setTotalPagado(totalPagado);
        
        CompraEntity compraGuardada = compraRepository.save(compra);
                
        return convertirAResponseDTO(compraGuardada);
    }

    // OBTENER MIS COMPRAS - CLIENTES
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
    public CompraResponseDTO getCompraById(UUID compraId, UUID usuarioId) {
        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));

        // validar que la compra sea del usuario
        if (!compraEntity.getUsuario().getIdUsuario().equals(usuarioId)) {
            throw new BusinessRuleException("No tienes permisos para ver esta compra");
        }

        return convertirAResponseDTO(compraEntity);
    }

    // CANCELAR COMPRA SI ESTÁ EN PENDIENTE - CLIENTES
    @Transactional
    public void cancelarCompra(UUID compraId, UUID usuarioId) {
        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));

        if (!compraEntity.getUsuario().getIdUsuario().equals(usuarioId)) {
            throw new BusinessRuleException("No tienes permisos para cancelar esta compra");
        }

        if (compraEntity.getCompraEstado() != CompraEstado.PENDIENTE) {
            throw new BusinessRuleException("Solo se pueden cancelar las compras en estado PENDIENTE");
        }

        // Restaurar stock de productos
        for (CompraDetalleEntity detalle : compraEntity.getDetalles()) {
            ProductoEntity productoEntity = detalle.getProducto();
            productoEntity.setStockProducto(productoEntity.getStockProducto() + detalle.getCantidad());
            productoRepository.save(productoEntity);
        }

        compraEntity.setCompraEstado(CompraEstado.CANCELADO);
        compraRepository.save(compraEntity);
    }

    // OBTENER LAS COMPRAS - ADMIN
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
    public CompraResponseDTO putEstadoCompra(UUID compraId, ActualizarEstadoCompraDTO request, UUID adminId) {
        adminValidationService.validarAdmin(adminId);

        CompraEntity compraEntity = compraRepository.findByIdWithDetails(compraId)
            .orElseThrow(() -> new BusinessRuleException("Compra no encontrada"));
        
        CompraEstado nuevoEstado;
        try {
            nuevoEstado =CompraEstado.valueOf(request.getEstado().toUpperCase());
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
        
        return convertirAResponseDTO(compraActualizada);
    }

    private CompraResponseDTO convertirAResponseDTO(CompraEntity compra) {
        CompraResponseDTO response = new CompraResponseDTO();
        response.setIdCompra(compra.getIdCompra());
        response.setNumeroCompra(compra.getNumeroCompra());
        response.setTotalPagado(compra.getTotalPagado());
        response.setFechaCompra(compra.getFechaCompra());
        response.setEstado(compra.getCompraEstado().toString());
        response.setMetodoPago(compra.getIdMetodoPago().getMetodoPago());
        
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
