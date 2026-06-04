package com.fesc.tiendaOnline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.fesc.tiendaOnline.model.entity.ProductoEntity;

public interface ProductoRepository extends JpaRepository<ProductoEntity, UUID> {

    // OBTENER PRODUCTOS EN PAGINACION
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p")
    Page<ProductoEntity> findAllWithDetails(Pageable pageable);

    // BUSCAR POR NOMBRE DEL PRODUCTO POR COINCIDENCIA PARCIAL - LIKE
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario " +
            "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%'))",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
            "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    Page<ProductoEntity> buscarPorNombre(@Param("nombre") String nombre, Pageable pageable);

    // BUSCAR POR NOMBRE O DESCRIPCIÓN POR COINCIDENCIA PARCIAL
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario " +
           "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR LOWER(p.descripcionProducto) LIKE LOWER(CONCAT('%', :termino, '%'))",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
           "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR LOWER(p.descripcionProducto) LIKE LOWER(CONCAT('%', :termino, '%'))")
    Page<ProductoEntity> buscarPorTermino(@Param("termino") String termino, Pageable pageable);

    // BUSCAR POR NOMBRE CON ORDENACIÓN ESPECÍFICA
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario " +
           "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%')) " +
           "ORDER BY p.nombreProducto ASC",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
           "WHERE LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    Page<ProductoEntity> buscarPorNombreOrdenado(@Param("nombre") String nombre, Pageable pageable);

    // BUSCAR POR CATEGORIA Y NOMBRE
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario " +
           "WHERE p.categoria.idCategoria = :categoriaId " +
           "AND LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%'))",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p " +
           "WHERE p.categoria.idCategoria = :categoriaId " +
           "AND LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', :nombre, '%'))")
    Page<ProductoEntity> buscarPorCategoriaYNombre(@Param("categoriaId") UUID categoriaId, 
                                                    @Param("nombre") String nombre, 
                                                    Pageable pageable);

    // BUSCAR POR CATEGORIA (SIN FILTRO DE NOMBRE)
    @Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario WHERE p.categoria.idCategoria = :categoriaId",
           countQuery = "SELECT COUNT(p) FROM ProductoEntity p WHERE p.categoria.idCategoria = :categoriaId")
    Page<ProductoEntity> buscarPorCategoria(@Param("categoriaId") UUID categoriaId, Pageable pageable);

    // BUSCAR PRODUCTO POR ID
    @Query("SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario WHERE p.idProducto = :id")
    Optional<ProductoEntity> findByIdWithDetails(@Param("id") UUID id);

    // BLOQUEO PESIMISTA PARA OPERACIONES DE STOCK (PESSIMISTIC_WRITE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductoEntity p WHERE p.idProducto = :id")
    Optional<ProductoEntity> findByIdWithLock(@Param("id") UUID id);

    // VERIFICAR SI YA EXISTE UN PRODUCTO POR EL NOMBRE
    boolean existsByNombreProducto(String nombreProducto);
}
