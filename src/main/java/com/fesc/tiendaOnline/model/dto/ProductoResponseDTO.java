package com.fesc.tiendaOnline.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ProductoResponseDTO {

    private UUID idProducto;
    private String nombreProducto;
    private String descripcionProducto;
    private BigDecimal precioProducto;
    private Integer stockProducto;
    private String urlImagenProducto;
    private String nombreCategoria;
    private String nombreUsuario;
}
