package com.fesc.tiendaOnline.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.dto.CompraMetodoPagoResponseDTO;
import com.fesc.tiendaOnline.model.dto.CompraResponseDTO;
import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.entity.CompraDetalleEntity;
import com.fesc.tiendaOnline.model.entity.CompraEntity;
import com.fesc.tiendaOnline.model.entity.MetodoPagoCompraEntity;

@Component
public class CompraMapper {

    public CompraResponseDTO toResponse(CompraEntity compra) {
        CompraResponseDTO response = new CompraResponseDTO();
        response.setIdCompra(compra.getIdCompra());
        response.setNumeroCompra(compra.getNumeroCompra());
        response.setTotalPagado(compra.getTotalPagado());
        response.setFechaCompra(compra.getFechaCompra());
        response.setEstado(compra.getCompraEstado().toString());
        response.setMetodoPago(compra.getIdMetodoPago().getMetodoPago());
        response.setWompiTransaccionId(compra.getWompiTransaccionId());

        if (compra.getUsuario() != null) {
            CompraResponseDTO.CompraUsuarioResponseDTO usuarioDTO = new CompraResponseDTO.CompraUsuarioResponseDTO();
            usuarioDTO.setIdUsuario(compra.getUsuario().getIdUsuario());
            usuarioDTO.setNombre(compra.getUsuario().getNombre());
            usuarioDTO.setEmail(compra.getUsuario().getEmail());
            usuarioDTO.setTelefono(compra.getUsuario().getTelefono());
            usuarioDTO.setDireccion(compra.getUsuario().getDireccion());
            usuarioDTO.setCiudad(compra.getUsuario().getCiudad());
            usuarioDTO.setDepartamento(compra.getUsuario().getDepartamento());
            usuarioDTO.setPais(compra.getUsuario().getPais());
            response.setUsuario(usuarioDTO);
        }

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

    public CompraMetodoPagoResponseDTO toMetodoPagoResponse(MetodoPagoCompraEntity entity) {
        CompraMetodoPagoResponseDTO response = new CompraMetodoPagoResponseDTO();
        response.setIdMetodoPago(entity.getIdMetodoPago());
        response.setMetodoPago(entity.getMetodoPago());
        return response;
    }

    public PaginacionResponseDTO<CompraResponseDTO> toPaginacionResponse(Page<CompraEntity> page) {
        List<CompraResponseDTO> contenido = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PaginacionResponseDTO<>(
                contenido,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }
}
