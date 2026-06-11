-- =============================================================================
-- V3__create_refresh_token.sql
-- Script DDL idempotente para crear la tabla refresh_token y sus índices.
-- Req 16.2
-- Ejecutar una vez por base de datos: psql -U <usuario> -d <db> -f V3__create_refresh_token.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tabla: refresh_token
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_token (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    token            VARCHAR(36)  NOT NULL,
    id_usuario       UUID         NOT NULL,
    fecha_expiracion TIMESTAMP    NOT NULL,
    revocado         BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_token       PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_token UNIQUE      (token),
    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario)
        ON DELETE CASCADE
);

-- Índice sobre token para lookups rápidos en /auth/refresh y /auth/logout
CREATE INDEX IF NOT EXISTS idx_refresh_token_token
    ON refresh_token (token);

-- Índice sobre id_usuario para revokeAllByUsuarioIdUsuario y consultas por usuario
CREATE INDEX IF NOT EXISTS idx_refresh_token_id_usuario
    ON refresh_token (id_usuario);

-- =============================================================================
-- Script idempotente: el uso de IF NOT EXISTS garantiza que se puede
-- ejecutar varias veces sin errores en entornos de CI o re-despliegue.
-- =============================================================================
