package com.fesc.tiendaOnline.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class ReviewResponseDTO {

    private UUID idReview;
    private UUID idProducto;
    private UUID idUsuario;
    private String nombreUsuario;
    private Integer estrellas;
    private String comentario;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
