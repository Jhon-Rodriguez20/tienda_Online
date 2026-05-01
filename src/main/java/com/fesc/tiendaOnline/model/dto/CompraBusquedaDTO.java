package com.fesc.tiendaOnline.model.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CompraBusquedaDTO {

    private String numeroCompra;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String estado;
    private int pagina = 0;
    private int tamanio = 10;

    public static final int[] TAMANIOS_PERMITIDOS = { 10, 25, 50 };

    public void setTamanio(int tamanio) {
        boolean esPermitido = false;
        for (int t : TAMANIOS_PERMITIDOS) {
            if (t == tamanio) {
                esPermitido = true;
                break;
            }
        }
        this.tamanio = esPermitido ? tamanio : 10;
    }
}
