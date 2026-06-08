package com.fesc.tiendaOnline.model.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class CompraMetodoPagoResponseDTO {

    private UUID idMetodoPago;
    private String metodoPago;
}
