package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ProductoResponseDTO {

    private UUID idProducto;
    private String nombreProducto;
    private String descripcionProducto;
    private Double precioProducto;
    private Integer stockProducto;
    private String urlImagenProducto;
    private String nombreCategoria;
    private String nombreUsuario;
}
