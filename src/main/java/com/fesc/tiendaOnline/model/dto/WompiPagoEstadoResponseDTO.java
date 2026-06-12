package com.fesc.tiendaOnline.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class WompiPagoEstadoResponseDTO {

    private UUID compraId;
    private String numeroCompra;
    private String estadoCompra;
    private String wompiTransaccionId;
    private String estadoWompi;
    private LocalDateTime fechaActualizacion;
}
