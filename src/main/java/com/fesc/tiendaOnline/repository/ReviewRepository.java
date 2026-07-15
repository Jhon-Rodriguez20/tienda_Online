package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fesc.tiendaOnline.model.entity.ReviewEntity;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    Page<ReviewEntity> findByProductoIdProducto(UUID idProducto, Pageable pageable);

    Optional<ReviewEntity> findByUsuarioIdUsuarioAndProductoIdProducto(UUID idUsuario, UUID idProducto);

    @Query("SELECT AVG(r.estrellas) FROM ReviewEntity r WHERE r.producto.idProducto = :idProducto")
    Optional<Double> promedioEstrellasPorProducto(@Param("idProducto") UUID idProducto);

    @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.producto.idProducto = :idProducto")
    Long contarPorProducto(@Param("idProducto") UUID idProducto);

    @Query("SELECT r.estrellas, COUNT(r) FROM ReviewEntity r WHERE r.producto.idProducto = :idProducto GROUP BY r.estrellas")
    java.util.List<Object[]> contarPorEstrellas(@Param("idProducto") UUID idProducto);
}
