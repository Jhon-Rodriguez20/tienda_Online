package com.fesc.tiendaOnline.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fesc.tiendaOnline.model.entity.CompraEntity;

public interface CompraRepository extends JpaRepository<CompraEntity, UUID> {
    
       @Query("SELECT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.detalles d " +
              "LEFT JOIN FETCH d.producto " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "LEFT JOIN FETCH c.usuario " +
              "WHERE c.idCompra = :id")
       Optional<CompraEntity> findByIdWithDetails(@Param("id") UUID id);
    
       @Query(value = "SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.usuario.idUsuario = :usuarioId",
              countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c WHERE c.usuario.idUsuario = :usuarioId")
       Page<CompraEntity> findByUsuarioId(@Param("usuarioId") UUID usuarioId, Pageable pageable);
       
       @Query(value = "SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago",
              countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c")
       Page<CompraEntity> findAllWithDetails(Pageable pageable);
       
       @Query("SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.numeroCompra = :numeroCompra")
       Optional<CompraEntity> findByNumeroCompra(@Param("numeroCompra") String numeroCompra);
       
       @Query("SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.usuario.idUsuario = :usuarioId AND c.numeroCompra = :numeroCompra")
       Optional<CompraEntity> findByUsuarioIdAndNumeroCompra(@Param("usuarioId") UUID usuarioId, 
                                                               @Param("numeroCompra") String numeroCompra);
       
       @Query(value = "SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.usuario.idUsuario = :usuarioId AND c.fechaCompra BETWEEN :fechaInicio AND :fechaFin",
              countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c " +
              "WHERE c.usuario.idUsuario = :usuarioId AND c.fechaCompra BETWEEN :fechaInicio AND :fechaFin")
       Page<CompraEntity> findByUsuarioIdAndFechaBetween(@Param("usuarioId") UUID usuarioId,
                                                        @Param("fechaInicio") LocalDateTime fechaInicio,
                                                        @Param("fechaFin") LocalDateTime fechaFin,
                                                        Pageable pageable);

       @Query(value = "SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.fechaCompra BETWEEN :fechaInicio AND :fechaFin",
              countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c " +
              "WHERE c.fechaCompra BETWEEN :fechaInicio AND :fechaFin")
       Page<CompraEntity> findByFechaBetween(@Param("fechaInicio") LocalDateTime fechaInicio,
                                                 @Param("fechaFin") LocalDateTime fechaFin,
                                                 Pageable pageable);
       
       boolean existsByNumeroCompra(String numeroCompra);

       // ── Queries by estado ─────────────────────────────────────────────────────

       @Query(value = "SELECT DISTINCT c FROM CompraEntity c " +
              "LEFT JOIN FETCH c.usuario " +
              "LEFT JOIN FETCH c.idMetodoPago " +
              "WHERE c.usuario.idUsuario = :usuarioId AND c.compraEstado = :estado",
              countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c " +
              "WHERE c.usuario.idUsuario = :usuarioId AND c.compraEstado = :estado")
       Page<CompraEntity> findByUsuarioIdAndEstado(@Param("usuarioId") UUID usuarioId,
                                                   @Param("estado") com.fesc.tiendaOnline.model.entity.CompraEstado estado,
                                                   Pageable pageable);

       @Query(value = "SELECT c FROM CompraEntity c " +
              "JOIN FETCH c.usuario " +
              "JOIN FETCH c.idMetodoPago " +
              "WHERE c.compraEstado = :estado",
              countQuery = "SELECT COUNT(c) FROM CompraEntity c " +
              "WHERE c.compraEstado = :estado")
       Page<CompraEntity> findByEstado(@Param("estado") com.fesc.tiendaOnline.model.entity.CompraEstado estado,
                                       Pageable pageable);

       @Query("SELECT c FROM CompraEntity c WHERE c.wompiTransaccionId = :wompiTransaccionId")
       Optional<CompraEntity> findByWompiTransaccionId(@Param("wompiTransaccionId") String wompiTransaccionId);

       // ── Query dinámica con filtros combinados (Admin) ─────────────────────────
       // ── Filtros combinados: estado + rango de fechas (Admin) ────────────────
       @Query(value = "SELECT c FROM CompraEntity c " +
              "JOIN FETCH c.usuario " +
              "JOIN FETCH c.idMetodoPago " +
              "WHERE c.compraEstado = :estado " +
              "AND c.fechaCompra >= :fechaInicio " +
              "AND c.fechaCompra <= :fechaFin",
              countQuery = "SELECT COUNT(c) FROM CompraEntity c " +
              "WHERE c.compraEstado = :estado " +
              "AND c.fechaCompra >= :fechaInicio " +
              "AND c.fechaCompra <= :fechaFin")
       Page<CompraEntity> findAllWithEstadoAndFechas(
              @Param("estado") com.fesc.tiendaOnline.model.entity.CompraEstado estado,
              @Param("fechaInicio") LocalDateTime fechaInicio,
              @Param("fechaFin") LocalDateTime fechaFin,
              Pageable pageable);

       // ── Filtros combinados: estado + rango de fechas (Cliente) ────────────────
       @Query(value = "SELECT c FROM CompraEntity c " +
              "JOIN FETCH c.usuario " +
              "JOIN FETCH c.idMetodoPago " +
              "WHERE c.usuario.idUsuario = :usuarioId " +
              "AND c.compraEstado = :estado " +
              "AND c.fechaCompra >= :fechaInicio " +
              "AND c.fechaCompra <= :fechaFin",
              countQuery = "SELECT COUNT(c) FROM CompraEntity c " +
              "WHERE c.usuario.idUsuario = :usuarioId " +
              "AND c.compraEstado = :estado " +
              "AND c.fechaCompra >= :fechaInicio " +
              "AND c.fechaCompra <= :fechaFin")
       Page<CompraEntity> findByUsuarioIdWithEstadoAndFechas(
              @Param("usuarioId") UUID usuarioId,
              @Param("estado") com.fesc.tiendaOnline.model.entity.CompraEstado estado,
              @Param("fechaInicio") LocalDateTime fechaInicio,
              @Param("fechaFin") LocalDateTime fechaFin,
              Pageable pageable);

       @Query("SELECT COUNT(c) > 0 FROM CompraEntity c JOIN c.detalles d " +
              "WHERE c.usuario.idUsuario = :idUsuario " +
              "AND c.compraEstado IN :estados " +
              "AND d.producto.idProducto = :idProducto")
       boolean existsByUsuarioAndEstadoAndProducto(@Param("idUsuario") UUID idUsuario,
                                                   @Param("estados") java.util.List<com.fesc.tiendaOnline.model.entity.CompraEstado> estados,
                                                   @Param("idProducto") UUID idProducto);
}