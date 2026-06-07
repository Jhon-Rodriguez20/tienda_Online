-- =============================================================================
-- V2__add_indexes.sql
-- Script DDL idempotente para crear índices de rendimiento en PostgreSQL.
-- Requiere permisos de superusuario o rol CREATEROLE para CREATE EXTENSION.
-- Ejecutar una vez por base de datos: psql -U <usuario> -d <db> -f V2__add_indexes.sql
-- =============================================================================

-- Habilitar extensión pg_trgm (necesaria para índices GIN de búsqueda por similitud)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- -----------------------------------------------------------------------------
-- Tabla: compra
-- -----------------------------------------------------------------------------

-- Req 14.1: Acelera findByUsuarioId, findByUsuarioIdAndNumeroCompra,
--           findByUsuarioIdAndFechaBetween
CREATE INDEX IF NOT EXISTS idx_compra_id_usuario
    ON compra(id_usuario);

-- Req 14.2: Acelera findByFechaBetween, findByUsuarioIdAndFechaBetween
CREATE INDEX IF NOT EXISTS idx_compra_fecha_compra
    ON compra(fecha_compra);

-- Req 14.3: Acelera filtros por estado de compra
CREATE INDEX IF NOT EXISTS idx_compra_estado
    ON compra(compra_estado);

-- -----------------------------------------------------------------------------
-- Tabla: compra_detalle
-- -----------------------------------------------------------------------------

-- Req 14.4: Acelera la carga de la relación @OneToMany detalles
--           (findByIdWithDetails y carga perezosa de detalles)
CREATE INDEX IF NOT EXISTS idx_compra_detalle_id_compra
    ON compra_detalle(id_compra);

-- Req 14.5: Acelera la navegación @ManyToOne producto y el bloqueo pesimista
--           (findByIdWithLock sobre ProductoEntity)
CREATE INDEX IF NOT EXISTS idx_compra_detalle_id_producto
    ON compra_detalle(id_producto);

-- -----------------------------------------------------------------------------
-- Tabla: producto
-- -----------------------------------------------------------------------------

-- Req 14.6: Acelera buscarPorCategoria y buscarPorCategoriaYNombre
CREATE INDEX IF NOT EXISTS idx_producto_categoria
    ON producto(id_producto_categoria);

-- Req 14.7: Índice GIN trigrama sobre nombre en minúsculas
--           Acelera búsquedas LIKE '%...%' en buscarPorNombre, buscarPorTermino,
--           buscarPorNombreOrdenado
CREATE INDEX IF NOT EXISTS idx_producto_nombre_trgm
    ON producto USING GIN (LOWER(nombre_producto) gin_trgm_ops);

-- Req 14.8: Índice GIN trigrama sobre descripción en minúsculas
--           Acelera búsquedas LIKE '%...%' en buscarPorTermino
CREATE INDEX IF NOT EXISTS idx_producto_descripcion_trgm
    ON producto USING GIN (LOWER(descripcion_producto) gin_trgm_ops);

-- -----------------------------------------------------------------------------
-- Tabla: usuario_codigo_verificacion
-- -----------------------------------------------------------------------------

-- Req 14.10: Acelera findByUsuario y deleteByIdUsuario
CREATE INDEX IF NOT EXISTS idx_codigo_verificacion_id_usuario
    ON usuario_codigo_verificacion(id_usuario);

-- =============================================================================
-- Nota Req 14.9: El índice sobre usuario(email) es generado automáticamente
-- por PostgreSQL al declarar la constraint UNIQUE. No requiere CREATE INDEX
-- explícito. Confirmar con: \d usuario en psql.
-- =============================================================================
