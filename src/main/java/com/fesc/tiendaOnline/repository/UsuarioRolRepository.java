package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fesc.tiendaOnline.model.entity.UsuarioRolEntity;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRolEntity, UUID> {

    Optional<UsuarioRolEntity> findByRolUsuario(String rolUsuario);
}
