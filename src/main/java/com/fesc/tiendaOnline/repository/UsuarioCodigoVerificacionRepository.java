package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fesc.tiendaOnline.model.entity.UsuarioCodigoVerificacionEntity;
import com.fesc.tiendaOnline.model.entity.UsuarioEntity;

import jakarta.transaction.Transactional;

public interface UsuarioCodigoVerificacionRepository extends JpaRepository<UsuarioCodigoVerificacionEntity, UUID> {
    
    Optional<UsuarioCodigoVerificacionEntity> findByUsuario(UsuarioEntity usuario);
    
    @Modifying
    @Transactional
    void deleteByUsuario(UsuarioEntity usuario);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM UsuarioCodigoVerificacionEntity u WHERE u.usuario.idUsuario = :idUsuario")
    void deleteByIdUsuario(@Param("idUsuario") UUID idUsuario);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM UsuarioCodigoVerificacionEntity u WHERE u.usuario = :usuario")
    void deleteByUsuarioEntity(@Param("usuario") UsuarioEntity usuario);
}
