package com.fesc.tiendaOnline.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.fesc.tiendaOnline.model.dto.PaginacionResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoCategoriaResponseDTO;
import com.fesc.tiendaOnline.model.dto.ProductoResponseDTO;
import com.fesc.tiendaOnline.model.entity.CategoriaEntity;
import com.fesc.tiendaOnline.model.entity.ProductoEntity;

@Component
public class ProductoMapper {

    public ProductoResponseDTO toResponse(ProductoEntity producto) {
        ProductoResponseDTO response = new ProductoResponseDTO();
        response.setIdProducto(producto.getIdProducto());
        response.setNombreProducto(producto.getNombreProducto());
        response.setDescripcionProducto(producto.getDescripcionProducto());
        response.setPrecioProducto(producto.getPrecioProducto());
        response.setStockProducto(producto.getStockProducto());
        response.setUrlImagenProducto(producto.getUrlImagenProducto());
        response.setNombreCategoria(producto.getCategoria().getNombreCategoria());
        response.setNombreUsuario(producto.getUsuario().getNombre());
        return response;
    }

    public ProductoCategoriaResponseDTO toCategoriaResponse(CategoriaEntity categoria) {
        ProductoCategoriaResponseDTO response = new ProductoCategoriaResponseDTO();
        response.setIdCategoria(categoria.getIdCategoria());
        response.setNombreCategoria(categoria.getNombreCategoria());
        return response;
    }

    public PaginacionResponseDTO<ProductoResponseDTO> toPaginacionResponse(Page<ProductoEntity> page) {
        List<ProductoResponseDTO> contenido = page.getContent().stream()
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
