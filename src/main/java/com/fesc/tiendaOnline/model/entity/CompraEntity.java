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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "compra")
public class CompraEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "idCompra", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idCompra;

    @Column(name = "numeroCompra", length = 6, nullable = false, unique = true)
    private String numeroCompra;

    @Column(name = "totalPagado", nullable = false)
    private Double totalPagado;

    @Column(name = "fechaCompra", nullable = false)
    private LocalDateTime fechaCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "compraEstado", nullable = false)
    private CompraEstado compraEstado;

    @ManyToOne
    @JoinColumn(name = "idMetodoPago", referencedColumnName = "idMetodoPago", nullable = false)
    private MetodoPagoCompraEntity idMetodoPago;

    @ManyToOne
    @JoinColumn(name = "idUsuario", referencedColumnName = "idUsuario", nullable = false)
    private UsuarioEntity usuario;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CompraDetalleEntity> detalles = new ArrayList<>();
}
