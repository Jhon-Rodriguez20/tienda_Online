package com.fesc.tiendaOnline.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuario_rol")
public class UsuarioRolEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_usuario_rol", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idUsuarioRol;

    @Column(name = "rol_usuario", length = 13, nullable = false, unique = true)
    private String rolUsuario;
    
    @OneToMany(mappedBy = "usuarioRol", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UsuarioEntity> usuarios = new ArrayList<>();

}
