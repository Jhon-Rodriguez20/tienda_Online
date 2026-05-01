package com.fesc.tiendaOnline.model.entity;

import java.time.LocalDateTime;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuario")
public class UsuarioEntity {
    
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "idUsuario", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idUsuario;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private UsuarioEstado estado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "idUsuarioRol", referencedColumnName = "idUsuarioRol", nullable = false)
    private UsuarioRolEntity usuarioRol;

    @Column(name = "urlImagen", length = 100, nullable = false)
    private String urlImagen;

    @Column(name = "intentosEnvioCodigoVerificacion", nullable = false)
    private Integer intentosEnvioCodigoVerificacion = 0;

    @Column(name = "bloqueadoHasta", nullable = true)
    private LocalDateTime bloqueadoHasta;

    @Column(name = "contrasenaEncp", length = 100, nullable = false)
    private String contrasenaEncp;
    
    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UsuarioCodigoVerificacionEntity codigoVerificacion;
}
