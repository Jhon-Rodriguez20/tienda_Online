package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fesc.tiendaOnline.model.entity.MetodoPagoCompraEntity;

public interface MetodoPagoRepository extends JpaRepository<MetodoPagoCompraEntity, UUID> {

    Optional<MetodoPagoCompraEntity> findByMetodoPago(String metodoPago);
}
