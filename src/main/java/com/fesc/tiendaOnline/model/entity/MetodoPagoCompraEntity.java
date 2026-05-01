package com.fesc.tiendaOnline.model.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "compraMetodoPago")
public class MetodoPagoCompraEntity {
    
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "idMetodoPago", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idMetodoPago;

    @Column(name = "metodoPago", length = 50, nullable = false, unique = true)
    private String metodoPago;
}
