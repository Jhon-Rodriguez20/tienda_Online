package com.fesc.tiendaOnline.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fesc.tiendaOnline.model.entity.CompraDetalleEntity;

public interface CompraDetalleRepository extends JpaRepository<CompraDetalleEntity, UUID> {
    
}