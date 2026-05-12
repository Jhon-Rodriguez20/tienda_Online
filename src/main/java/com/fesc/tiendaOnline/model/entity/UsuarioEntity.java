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
    @Column(name = "id_usuario", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID idUsuario;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "telefono", length = 20, nullable = false, unique = true)
    private String telefono;

    @Column(name = "pais", length = 30, nullable = false)
    private String pais;

    @Column(name = "direccion", length = 100, nullable = false)
    private String direccion;

    @Column(name = "departamento", length = 50, nullable = false)
    private String departamento;

    @Column(name = "ciudad", length = 50, nullable = false)
    private String ciudad;

    @Column(name = "codigo_postal", length = 17, nullable = false)
    private String codigoPostal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private UsuarioEstado estado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_rol", referencedColumnName = "id_usuario_rol", nullable = false)
    private UsuarioRolEntity usuarioRol;

    @Column(name = "url_imagen", length = 100, nullable = false)
    private String urlImagen;

    @Column(name = "intentos_envio_codigo_verificacion", nullable = false)
    private Integer intentosEnvioCodigoVerificacion = 0;

    @Column(name = "bloqueado_hasta", nullable = true)
    private LocalDateTime bloqueadoHasta;

    @Column(name = "contrasenaEncp", length = 100, nullable = false)
    private String contrasenaEncp;
    
    @OneToOne(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UsuarioCodigoVerificacionEntity codigoVerificacion;
}
