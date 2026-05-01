package com.fesc.tiendaOnline.model.dto;

import lombok.Data;

@Data
public class PaginacionRequestDTO {

    private int pagina = 0;
    private int tamanio = 10;

    public static final int[] TAMANIOS_PERMITIDOS = {10, 25, 50};

    public void setTamanio(int tamanio) {
        
        boolean esPermitido = false;
        for (int t: TAMANIOS_PERMITIDOS) {
            if (t == tamanio) {
                esPermitido = true;
                break;
            }
        }
        if (!esPermitido) {
            this.tamanio = 10;
        } else {
            this.tamanio = tamanio;
        }
    }
}
