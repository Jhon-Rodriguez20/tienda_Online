package com.fesc.tiendaOnline.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class CompraResponseDTO {

    private UUID idCompra;
    private String numeroCompra;
    private Double totalPagado;
    private LocalDateTime fechaCompra;
    private String estado;
    private String metodoPago;
    private String wompiTransaccionId;
    private String asyncPaymentUrl;
    private CompraUsuarioResponseDTO usuario;
    private List<CompraDetalleResponseDTO> detalles;

    @Data
    public static class CompraUsuarioResponseDTO {
        private UUID idUsuario;
        private String nombre;
        private String email;
        private String telefono;
        private String direccion;
        private String ciudad;
        private String departamento;
        private String pais;
    }

    @Data
    public static class CompraDetalleResponseDTO {
        private UUID idProducto;
        private String nombreProducto;
        private Integer cantidad;
        private Double precioUnitario;
        private Double subtotal;
    }
}
