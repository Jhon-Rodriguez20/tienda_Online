package com.fesc.tiendaOnline.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "compra", indexes = {
    @Index(name = "idx_compra_id_usuario", columnList = "id_usuario"),
    @Index(name = "idx_compra_fecha_compra", columnList = "fecha_compra"),
    @Index(name = "idx_compra_estado", columnList = "compra_estado")
})
public class CompraEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_compra", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idCompra;

    @Column(name = "numero_compra", length = 6, nullable = false, unique = true)
    private String numeroCompra;

    @Column(name = "total_pagado", nullable = false)
    private Double totalPagado;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDateTime fechaCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "compra_estado", nullable = false)
    private CompraEstado compraEstado;

    @Column(name = "wompi_transaccion_id", length = 50, nullable = true)
    private String wompiTransaccionId;

    @ManyToOne
    @JoinColumn(name = "id_metodo_pago", referencedColumnName = "id_metodo_pago", nullable = false)
    private MetodoPagoCompraEntity idMetodoPago;

    @ManyToOne
    @JoinColumn(name = "id_usuario", referencedColumnName = "id_usuario", nullable = false)
    private UsuarioEntity usuario;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<CompraDetalleEntity> detalles = new ArrayList<>();
    
    public void addDetalle(CompraDetalleEntity detalle) {
        detalles.add(detalle);
        detalle.setCompra(this);
    }
}
