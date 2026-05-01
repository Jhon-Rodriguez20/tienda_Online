package com.fesc.tiendaOnline.model.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "producto")
public class ProductoEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "idProducto", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idProducto;

    @Column(name = "nombreProducto", length = 50, nullable = false, unique = true)
    private String nombreProducto;

    @Column(name = "descripcionProducto", length = 200, nullable = false)
    private String descripcionProducto;

    @Column(name = "precioProducto", nullable = false)
    private Double precioProducto;

    @Column(name = "stockProducto", nullable = false)
    private Integer stockProducto;

    @Column(name = "urlImagenProducto", length = 150, nullable = false)
    private String urlImagenProducto;

    @ManyToOne
    @JoinColumn(name = "idProductoCategoria", referencedColumnName = "idCategoria", nullable = false)
    private CategoriaEntity categoria;

    @ManyToOne
    @JoinColumn(name = "idUsuario", referencedColumnName = "idUsuario", nullable = false)
    private UsuarioEntity usuario;
}
