package com.fesc.tiendaOnline.model.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompraRequestDTO {

    @NotNull(message = "El método de pago es obligatorio")
    private UUID idMetodoPago;

    @NotNull(message = "Debes de seleccionar al menos un producto")
    @Size(min = 1, message = "Debes de seleccionar al menos un producto")
    private List<ItemCompraDTO> items;

    @Data
    public static class ItemCompraDTO {

        @NotNull(message = "El ID del producto es obligatorio")
        private UUID idProducto;

        @NotNull(message = "La cantidad es obligatoria")
        private Integer cantidad;
    }
}
