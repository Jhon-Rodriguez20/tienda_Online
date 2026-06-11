package com.fesc.tiendaOnline.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fesc.tiendaOnline.model.entity.RefreshTokenEntity;

import jakarta.transaction.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByToken(String token);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity r SET r.revocado = true WHERE r.usuario.idUsuario = :idUsuario")
    void revokeAllByUsuarioIdUsuario(@Param("idUsuario") UUID idUsuario);

    @Modifying
    @Transactional
    void deleteByFechaExpiracionBefore(LocalDateTime fecha);
}
