package com.fesc.tiendaOnline.model.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ErrorResponseDTO {

    private String error;
    private String mensaje;
    private int status;
    private String path;
    private LocalDateTime timestamp;

    public ErrorResponseDTO(String error, String mensaje, int status, String path) {
        this.error = error;
        this.mensaje = mensaje;
        this.status = status;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}
