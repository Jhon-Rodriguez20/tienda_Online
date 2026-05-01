package com.fesc.tiendaOnline.model.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarioCodigoVerificacion", 
    uniqueConstraints = @UniqueConstraint(columnNames = "idUsuario"))
public class UsuarioCodigoVerificacionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idUsuario", referencedColumnName = "idUsuario", nullable = false, unique = true)
    private UsuarioEntity usuario;

    @Column(name = "codigoVerificacion", length = 6, nullable = false)
    private String codigoVerificacion;

    @Column(name = "expiracion", nullable = false)
    private LocalDateTime expiracion;
}
