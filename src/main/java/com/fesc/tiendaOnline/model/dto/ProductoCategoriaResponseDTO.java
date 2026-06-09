package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class ProductoCategoriaResponseDTO {

    private UUID idCategoria;
    private String nombreCategoria;
}
