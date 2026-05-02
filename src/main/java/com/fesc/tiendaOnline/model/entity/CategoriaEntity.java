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
@Table(name = "categoria")
public class CategoriaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_categoria", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idCategoria;

    @Column(name = "nombre_categoria", length = 50, nullable = false, unique = true)
    private String nombreCategoria;

    @Column(name = "descripcion_categoria", length = 200, nullable = false)
    private String descripcionCategoria;
}
