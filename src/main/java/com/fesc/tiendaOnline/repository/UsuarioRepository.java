package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fesc.tiendaOnline.model.entity.UsuarioEntity;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {

    // Buscar usuario por email
    Optional<UsuarioEntity> findByEmail(String email);
    
    // Buscar usuario con su rol cargado
    @Query("SELECT u FROM UsuarioEntity u JOIN FETCH u.usuarioRol WHERE u.email = :email")
    Optional<UsuarioEntity> findByEmailWithRol(@Param("email") String email);
    
    // Buscar usuario con su código de verificación
    @Query("SELECT u FROM UsuarioEntity u LEFT JOIN FETCH u.codigoVerificacion WHERE u.idUsuario = :id")
    Optional<UsuarioEntity> findByIdWithCodigo(@Param("id") UUID id);

    @Query("SELECT u FROM UsuarioEntity u JOIN FETCH u.usuarioRol WHERE u.idUsuario = :id")
    Optional<UsuarioEntity> findByIdWithRol(@Param("id") UUID id);
}
