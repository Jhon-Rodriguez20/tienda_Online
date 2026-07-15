package com.fesc.tiendaOnline.model.dto;

import java.util.Map;

import lombok.Data;

@Data
public class ReviewEstadisticasDTO {

    private Double promedioEstrellas;
    private Long totalResenas;
    private Map<Integer, Long> distribucion;
}
