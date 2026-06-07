package com.fesc.tiendaOnline.model.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "producto", indexes = {
    @Index(name = "idx_producto_categoria", columnList = "id_producto_categoria")
})
public class ProductoEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_producto", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idProducto;

    @Column(name = "nombre_producto", length = 50, nullable = false, unique = true)
    private String nombreProducto;

    @Column(name = "descripcion_producto", length = 200, nullable = false)
    private String descripcionProducto;

    @Column(name = "precio_producto", nullable = false)
    private Double precioProducto;

    @Column(name = "stock_producto", nullable = false)
    private Integer stockProducto;

    @Column(name = "url_imagen_producto", length = 150, nullable = false)
    private String urlImagenProducto;

    @ManyToOne
    @JoinColumn(name = "id_producto_categoria", referencedColumnName = "id_categoria", nullable = false)
    private CategoriaEntity categoria;

    @ManyToOne
    @JoinColumn(name = "id_usuario", referencedColumnName = "id_usuario", nullable = false)
    private UsuarioEntity usuario;
}
