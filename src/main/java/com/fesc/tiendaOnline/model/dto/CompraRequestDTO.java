package com.fesc.tiendaOnline.model.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompraRequestDTO {

    @NotNull(message = "El método de pago es obligatorio")
    private UUID idMetodoPago;

    @NotNull(message = "Debes de seleccionar al menos un producto")
    @Size(min = 1, message = "Debes de seleccionar al menos un producto")
    private List<ItemCompraDTO> items;

    @Pattern(regexp = "BANCOLOMBIA_TRANSFER|NEQUI|CARD",
            message = "El tipo de pago Wompi debe ser BANCOLOMBIA_TRANSFER, NEQUI o CARD")
    private String wompiTipoPago;

    private String wompiCardToken;

    private String wompiNequiPhone;

    @Min(value = 1, message = "Las cuotas mínimas son 1")
    @Max(value = 36, message = "Las cuotas máximas son 36")
    private Integer cuotas = 1;

    @Data
    public static class ItemCompraDTO {

        @NotNull(message = "El ID del producto es obligatorio")
        private UUID idProducto;

        @NotNull(message = "La cantidad es obligatoria")
        private Integer cantidad;
    }
}
