-- ═══════════════════════════════════════════════════════════════════════════════
-- SCRIPT DE DATOS MASIVOS PARA PRUEBAS DE RENDIMIENTO
-- Tienda Online — PostgreSQL
-- ═══════════════════════════════════════════════════════════════════════════════
--
-- PRERREQUISITOS:
--   1. La aplicación debe haber corrido al menos una vez para que los
--      DataLoaders creen: roles, categorías, métodos de pago y admin.
--   2. Ejecutar en pgAdmin → Query Tool → abrir este archivo → Execute (F5)
--
-- GENERA:
--   - 500 clientes
--   - 200 productos (distribuidos en las 8 categorías existentes)
--   - 2000 compras con ~4000 detalles
--   - ~1500 reviews
--
-- NOTA: La contraseña para TODOS los usuarios de prueba es: Test1234!
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── 1. Clientes masivos (500) ───────────────────────────────────────────────
-- Usa el rol CLIENTE y la imagen por defecto que ya existen en la BD
INSERT INTO usuario (
    id_usuario, nombre, apellido, email, telefono, pais, direccion,
    departamento, ciudad, codigo_postal, estado, id_usuario_rol,
    url_imagen, intentos_envio_codigo_verificacion, contrasena_encp
)
SELECT
    gen_random_uuid(),
    'Usuario' || s.i,
    'Apellido' || s.i,
    'usuario' || s.i || '@test.com',
    '30' || lpad(s.i::text, 8, '0'),
    'Colombia',
    'Calle ' || (s.i % 100 + 1) || ' # ' || (s.i % 50 + 1) || '-' || (s.i % 99 + 1),
    CASE (s.i % 5)
        WHEN 0 THEN 'Cundinamarca'
        WHEN 1 THEN 'Antioquia'
        WHEN 2 THEN 'Valle del Cauca'
        WHEN 3 THEN 'Santander'
        ELSE 'Atlántico'
    END,
    CASE (s.i % 5)
        WHEN 0 THEN 'Bogotá'
        WHEN 1 THEN 'Medellín'
        WHEN 2 THEN 'Cali'
        WHEN 3 THEN 'Bucaramanga'
        ELSE 'Barranquilla'
    END,
    lpad((110000 + s.i)::text, 6, '0'),
    'ACTIVO',
    (SELECT id_usuario_rol FROM usuario_rol WHERE rol_usuario = 'CLIENTE' LIMIT 1),
    '/images/sinImagenPerfil.webp',
    0,
    -- BCrypt hash de "Test1234!"
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
FROM generate_series(1, 500) AS s(i)
ON CONFLICT (email) DO NOTHING;

-- ─── 2. Productos masivos (200) ──────────────────────────────────────────────
-- Usa las 8 categorías del CategoriaDataLoader y el admin existente
INSERT INTO producto (
    id_producto, nombre_producto, descripcion_producto, precio_producto,
    stock_producto, url_imagen_producto, id_producto_categoria, id_usuario
)
SELECT
    gen_random_uuid(),
    'Producto ' || s.i || ' - Cat' || ((s.i - 1) % 8 + 1),
    'Descripción detallada del producto número ' || s.i || '. Alta calidad y garantía incluida.',
    round((random() * 495000 + 5000)::numeric, 2),
    (random() * 490 + 10)::int,
    '/uploads/ea12203d-a907-4d70-a2df-96cf55dafe71.jpg',
    (SELECT id_categoria FROM categoria ORDER BY id_categoria OFFSET ((s.i - 1) % 8) LIMIT 1),
    (SELECT id_usuario FROM usuario WHERE email = 'developjarz@gmail.com' LIMIT 1)
FROM generate_series(1, 200) AS s(i)
ON CONFLICT (nombre_producto) DO NOTHING;

-- ─── 3. Compras masivas (2000) ───────────────────────────────────────────────
-- Usa los 3 métodos de pago del MetodoPagoDataLoader
INSERT INTO compra (
    id_compra, numero_compra, total_pagado, fecha_compra,
    compra_estado, wompi_transaccion_id, id_metodo_pago, id_usuario
)
SELECT
    gen_random_uuid(),
    lpad(s.i::text, 6, '0'),
    round((random() * 900000 + 10000)::numeric, 2),
    NOW() - (random() * interval '180 days'),
    (ARRAY['PENDIENTE','ACEPTADO','ENTREGADO','CANCELADO'])[1 + (s.i % 4)],
    CASE WHEN (s.i % 4) != 0 THEN 'wompi_txn_' || s.i ELSE NULL END,
    (SELECT id_metodo_pago FROM compra_metodo_pago ORDER BY id_metodo_pago OFFSET (s.i % 3) LIMIT 1),
    (SELECT id_usuario FROM usuario
     WHERE estado = 'ACTIVO'
       AND id_usuario_rol = (SELECT id_usuario_rol FROM usuario_rol WHERE rol_usuario = 'CLIENTE' LIMIT 1)
     ORDER BY id_usuario
     OFFSET (s.i % 500) LIMIT 1)
FROM generate_series(1, 2000) AS s(i)
ON CONFLICT (numero_compra) DO NOTHING;

-- ─── 4. Detalles de compra (~4000, 2 items por compra) ───────────────────────
INSERT INTO compra_detalle (
    id_compra_detalle, id_compra, id_producto, cantidad, precio_unitario, subtotal
)
SELECT
    gen_random_uuid(),
    c.id_compra,
    p.id_producto,
    qty,
    p.precio_producto,
    p.precio_producto * qty
FROM compra c
CROSS JOIN LATERAL (
    SELECT id_producto, precio_producto
    FROM producto
    ORDER BY random()
    LIMIT 2
) p
CROSS JOIN LATERAL (
    SELECT (floor(random() * 3) + 1)::int AS qty
) q;

-- ─── 5. Reviews masivas (~1600, distribuidas en todos los productos) ──────────
-- Asigna ~8 reviews aleatorias a cada producto de usuarios clientes aleatorios.
-- ON CONFLICT evita duplicados (un usuario solo puede reseñar un producto una vez).
INSERT INTO review (
    id_review, id_producto, id_usuario, estrellas, comentario, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    p.id_producto,
    u.id_usuario,
    (floor(random() * 5) + 1)::int,
    (ARRAY[
        'Excelente producto, muy recomendado. Llegó a tiempo y en perfectas condiciones.',
        'Buena calidad por el precio. Cumple con lo prometido en la descripción.',
        'Regular, esperaba un poco más. El empaque podría mejorar bastante.',
        'Muy bueno, lo volvería a comprar sin pensarlo dos veces.',
        'Producto decente pero el envío tardó demasiado. Mejorar logística.',
        'Increíble relación calidad-precio. Superó mis expectativas totalmente.',
        'Funciona bien, pero las instrucciones podrían ser más claras.',
        'Buen producto en general. Lo recomiendo para uso diario sin duda.'
    ])[1 + (floor(random() * 8))::int],
    NOW() - (random() * interval '90 days'),
    NOW() - (random() * interval '30 days')
FROM producto p
CROSS JOIN LATERAL (
    SELECT id_usuario
    FROM usuario
    WHERE estado = 'ACTIVO'
      AND id_usuario_rol = (SELECT id_usuario_rol FROM usuario_rol WHERE rol_usuario = 'CLIENTE' LIMIT 1)
    ORDER BY random()
    LIMIT 8
) u
ON CONFLICT ON CONSTRAINT uk_review_usuario_producto DO NOTHING;

-- ─── 6. Actualizar estadísticas de PostgreSQL ────────────────────────────────
ANALYZE usuario;
ANALYZE producto;
ANALYZE compra;
ANALYZE compra_detalle;
ANALYZE review;

-- ─── Resumen de datos insertados ─────────────────────────────────────────────
SELECT 'Roles' AS tabla, count(*) FROM usuario_rol
UNION ALL SELECT 'Métodos Pago', count(*) FROM compra_metodo_pago
UNION ALL SELECT 'Categorías', count(*) FROM categoria
UNION ALL SELECT 'Usuarios', count(*) FROM usuario
UNION ALL SELECT 'Productos', count(*) FROM producto
UNION ALL SELECT 'Compras', count(*) FROM compra
UNION ALL SELECT 'Detalles Compra', count(*) FROM compra_detalle
UNION ALL SELECT 'Reviews', count(*) FROM review;
