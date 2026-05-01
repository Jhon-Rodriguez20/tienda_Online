package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fesc.tiendaOnline.model.entity.CategoriaEntity;

public interface CategoriaRepository extends JpaRepository<CategoriaEntity, UUID> {

    Optional<CategoriaEntity> findByNombreCategoria(String nombreCategoria);
}
