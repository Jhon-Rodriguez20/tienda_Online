package com.fesc.tiendaOnline.model.dto;

import java.util.List;

import lombok.Data;

@Data
public class PaginacionResponseDTO<T> {

    private List<T> contenido;
    private int numeroPagina;
    private int tamanioPagina;
    private long totalElementos;
    private int totalPaginas;
    private boolean esUltima;
    private boolean esPrimera;
    
    public PaginacionResponseDTO(List<T> contenido, int numeroPagina, int tamanioPagina, long totalElementos,
            int totalPaginas, boolean esUltima, boolean esPrimera) {
        
        this.contenido = contenido;
        this.numeroPagina = numeroPagina;
        this.tamanioPagina = tamanioPagina;
        this.totalElementos = totalElementos;
        this.totalPaginas = totalPaginas;
        this.esUltima = esUltima;
        this.esPrimera = esPrimera;
    }
}
