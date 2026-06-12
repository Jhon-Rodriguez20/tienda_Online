-- =============================================================================
-- V4__wompi.sql
-- Script DDL idempotente para agregar la columna wompi_transaccion_id en compra.
-- Req 19.7
-- Ejecutar una vez por base de datos: psql -U <usuario> -d <db> -f V4__wompi.sql
-- =============================================================================

-- Req 19.7: Almacena el identificador de transacción de Wompi asociado a la compra
ALTER TABLE compra
    ADD COLUMN IF NOT EXISTS wompi_transaccion_id VARCHAR(50) NULL;

-- =============================================================================
-- Script idempotente: IF NOT EXISTS garantiza ejecución segura en cualquier
-- entorno sin errores si la columna ya existe.
-- =============================================================================
