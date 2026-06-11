# Requirements Document

## Introduction

Este documento describe los requisitos para cuatro mejoras técnicas en la tienda online construida con Spring Boot 4, Java 25 y PostgreSQL. Las mejoras abordan: (1) idempotencia en operaciones críticas de compra mediante clave de idempotencia en el header HTTP, (2) refuerzo de los principios ACID en las transacciones de `CompraService` y `ProductoService`, (3) caché en memoria con Caffeine para consultas de productos, y (4) corrección de problemas N+1 en las entidades y repositorios JPA.

---

## Glossary

- **CompraService**: Servicio Spring que gestiona el ciclo de vida de las compras (crear, cancelar, actualizar estado).
- **ProductoService**: Servicio Spring que gestiona el CRUD de productos y las búsquedas paginadas.
- **CompraController**: Controlador REST que expone los endpoints de compras bajo `/compras`.
- **ProductoController**: Controlador REST que expone los endpoints de productos bajo `/productos`.
- **CompraEntity**: Entidad JPA que representa una compra; contiene una relación `@OneToMany` con `CompraDetalleEntity`.
- **CompraDetalleEntity**: Entidad JPA que representa un ítem de compra; contiene relaciones `@ManyToOne` con `CompraEntity` y `ProductoEntity`.
- **ProductoEntity**: Entidad JPA que representa un producto del catálogo.
- **CompraRepository**: Repositorio Spring Data JPA para operaciones sobre `CompraEntity`.
- **ProductoRepository**: Repositorio Spring Data JPA para operaciones sobre `ProductoEntity`.
- **IdempotencyStore**: Componente en memoria (mapa concurrente) que almacena el par `{claveIdempotencia → CompraResponseDTO}` para evitar re-ejecución de operaciones.
- **Clave_Idempotencia**: Valor UUID v4 enviado por el cliente en el header HTTP `Idempotency-Key` para identificar de forma única una solicitud.
- **CacheManager**: Bean de Spring Cache configurado con Caffeine que gestiona las regiones de caché `productos`, `productoPorId` y `busquedaProductos`.
- **Nivel_Aislamiento**: Nivel de aislamiento de transacción de base de datos (SERIALIZABLE, REPEATABLE_READ, READ_COMMITTED).
- **Problema_N_Plus_1**: Antipatrón JPA donde una consulta inicial genera N consultas adicionales para cargar asociaciones.
- **JOIN_FETCH**: Cláusula JPQL que fuerza la carga de asociaciones en una sola consulta SQL.
- **TTL**: Tiempo de vida (Time To Live) de una entrada en caché antes de ser invalidada automáticamente.

---

## Requirements

### Requirement 1: Idempotencia en la creación de compras

**User Story:** Como cliente, quiero que si envío la misma solicitud de compra más de una vez (por error de red o doble clic), el sistema no cree compras duplicadas ni descuente el stock dos veces, sino que devuelva la respuesta original de la primera solicitud exitosa.

#### Acceptance Criteria

1. WHEN el cliente envía `POST /compras/realizar` con un header `Idempotency-Key` cuyo valor es un UUID v4 válido, THE `CompraController` SHALL extraer la clave y delegarla al `CompraService` junto con el cuerpo de la solicitud.

2. WHEN el `CompraService` recibe una solicitud de creación de compra con una `Clave_Idempotencia` que ya existe en el `IdempotencyStore`, THE `CompraService` SHALL retornar el `CompraResponseDTO` almacenado previamente, previniendo explícitamente la ejecución de cualquier lógica de negocio y sin modificar la base de datos.

3. WHEN el `CompraService` recibe una solicitud de creación de compra con una `Clave_Idempotencia` que no existe en el `IdempotencyStore`, o cuya entrada ha expirado, THE `CompraService` SHALL tratar la solicitud como nueva, ejecutar la lógica de `realizarCompra`, almacenar el `CompraResponseDTO` resultante en el `IdempotencyStore` asociado a esa clave, y retornar el resultado.

4. IF el cliente envía `POST /compras/realizar` sin el header `Idempotency-Key`, THEN THE `CompraController` SHALL retornar una respuesta HTTP 400 con un mensaje de error que indique que el header es obligatorio.

5. IF el valor del header `Idempotency-Key` no es un UUID v4 válido, THEN THE `CompraController` SHALL retornar una respuesta HTTP 400 con un mensaje de error que describa el formato esperado.

6. THE `IdempotencyStore` SHALL almacenar las entradas con un TTL de 24 horas, tras el cual la entrada expira y una nueva solicitud con la misma clave se trata como una solicitud nueva.

7. WHEN el `CompraService` retorna una respuesta idempotente desde el `IdempotencyStore`, THE `CompraController` SHALL incluir el header de respuesta `Idempotency-Replayed: true` en la respuesta HTTP.

---

### Requirement 2: Idempotencia en la actualización de estado de compra

**User Story:** Como administrador, quiero que si envío la misma solicitud de actualización de estado de una compra más de una vez, el sistema no aplique la transición de estado dos veces ni genere errores de concurrencia.

#### Acceptance Criteria

1. WHEN el administrador envía `PUT /compras/admin/{compraId}/estado` con un header `Idempotency-Key` válido, THE `CompraController` SHALL extraer la clave y delegarla al `CompraService` junto con el `compraId` y el `ActualizarEstadoCompraDTO`.

2. WHEN el `CompraService` recibe una solicitud de actualización de estado con una `Clave_Idempotencia` que ya existe en el `IdempotencyStore`, THE `CompraService` SHALL retornar el `CompraResponseDTO` almacenado previamente sin modificar el estado de la compra en la base de datos.

3. WHEN el `CompraService` recibe una solicitud de actualización de estado con una `Clave_Idempotencia` nueva, THE `CompraService` SHALL ejecutar la lógica de `putEstadoCompra`, almacenar el resultado en el `IdempotencyStore`, y retornar el `CompraResponseDTO` actualizado.

4. IF el administrador envía `PUT /compras/admin/{compraId}/estado` sin el header `Idempotency-Key`, THEN THE `CompraController` SHALL retornar una respuesta HTTP 400 con un mensaje de error que indique que el header es obligatorio.

---

### Requirement 3: Atomicidad en la creación de compras

**User Story:** Como desarrollador, quiero que la creación de una compra sea completamente atómica, de modo que si falla cualquier operación intermedia (descuento de stock, creación de detalle, guardado de compra), todos los cambios se reviertan y la base de datos quede en su estado original.

#### Acceptance Criteria

1. THE `CompraService` SHALL ejecutar el método `realizarCompra` dentro de una única transacción de base de datos con nivel de aislamiento `REPEATABLE_READ`.

2. WHEN el `CompraService` procesa los ítems de una compra, THE `CompraService` SHALL acumular todos los cambios de stock en memoria durante el loop, garantizar que se ejecute exactamente una operación de guardado por `ProductoEntity` modificado al finalizar el procesamiento de todos los ítems, y no llamar a `productoRepository.save(producto)` dentro del loop.

3. IF cualquier operación dentro de `realizarCompra` lanza una excepción (stock insuficiente, producto no encontrado, fallo de persistencia), THEN THE `CompraService` SHALL revertir la transacción completa, dejando el stock de todos los productos y el registro de compra sin modificar.

4. THE `CompraService` SHALL guardar la `CompraEntity` una única vez al final del método `realizarCompra`, aprovechando el mecanismo de cascada `CascadeType.ALL` para persistir los `CompraDetalleEntity` asociados en la misma operación.

---

### Requirement 4: Atomicidad en la cancelación de compras

**User Story:** Como desarrollador, quiero que la cancelación de una compra sea completamente atómica, de modo que la restauración del stock y el cambio de estado ocurran juntos o no ocurran en absoluto.

#### Acceptance Criteria

1. THE `CompraService` SHALL ejecutar el método `cancelarCompra` dentro de una única transacción de base de datos con nivel de aislamiento `REPEATABLE_READ`.

2. WHEN el `CompraService` restaura el stock durante la cancelación, THE `CompraService` SHALL acumular todos los cambios de stock en memoria durante el loop, garantizar que se ejecute exactamente una operación de guardado por `ProductoEntity` modificado al finalizar el procesamiento de todos los detalles, y no llamar a `productoRepository.save(productoEntity)` dentro del loop. Este patrón de acumulación en memoria se mantiene incluso cuando ocurre una excepción durante la restauración, ya que la transacción revertirá todos los cambios.

3. IF cualquier operación dentro de `cancelarCompra` lanza una excepción, THEN THE `CompraService` SHALL revertir la transacción completa, dejando el stock de todos los productos y el estado de la compra sin modificar.

---

### Requirement 5: Aislamiento y consistencia en operaciones de stock

**User Story:** Como desarrollador, quiero que las operaciones que modifican el stock de productos estén protegidas contra condiciones de carrera, de modo que dos compras simultáneas del mismo producto no puedan descontar más stock del disponible.

#### Acceptance Criteria

1. WHEN el `CompraService` carga un `ProductoEntity` para descontar stock dentro de `realizarCompra`, THE `CompraService` SHALL utilizar una consulta con bloqueo pesimista (`PESSIMISTIC_WRITE`) sobre el `ProductoEntity` para prevenir lecturas sucias y actualizaciones concurrentes del mismo registro.

2. WHILE una transacción de `realizarCompra` mantiene el bloqueo sobre un `ProductoEntity`, THE `CompraService` SHALL garantizar que ninguna otra transacción concurrente pueda modificar el stock de ese producto hasta que la transacción actual se confirme o revierta.

3. WHEN el `CompraService` carga un `ProductoEntity` para restaurar stock dentro de `cancelarCompra`, THE `CompraService` SHALL utilizar una consulta con bloqueo pesimista (`PESSIMISTIC_WRITE`) sobre el `ProductoEntity`.

---

### Requirement 6: Transacciones de solo lectura en métodos de consulta

**User Story:** Como desarrollador, quiero que los métodos de consulta en `CompraService` y `ProductoService` estén anotados con `@Transactional(readOnly = true)` para optimizar el rendimiento de la base de datos y evitar que Hibernate rastree cambios innecesariamente.

#### Acceptance Criteria

1. THE `CompraService` SHALL anotar los métodos `getMisCompras`, `getCompraById` y `getAllCompras` con `@Transactional(readOnly = true)`.

2. THE `ProductoService` SHALL anotar los métodos `getProductos`, `buscarProductosPorTermino`, `buscarProductosPorNombre`, `buscarProductosAvanzado` y `getProductoById` con `@Transactional(readOnly = true)`.

3. WHEN Hibernate ejecuta una transacción marcada como `readOnly = true`, THE `ProductoService` SHALL confiar en la semántica de `@Transactional(readOnly = true)` para garantizar que ningún cambio accidental sobre las entidades cargadas sea persistido en la base de datos al finalizar la transacción, sin requerir configuración adicional de flush mode ni detachment explícito de entidades.

---

### Requirement 7: Caché en memoria para listado de productos

**User Story:** Como usuario, quiero que el listado paginado de productos responda rápidamente incluso bajo carga, sin ejecutar la misma consulta a la base de datos repetidamente para los mismos parámetros de paginación.

#### Acceptance Criteria

1. THE `ProductoService` SHALL configurar Spring Cache con `CaffeineCacheManager` como implementación de caché en memoria, sin dependencia de Redis ni de ningún almacén externo.

2. WHEN el `ProductoService` ejecuta `getProductos(pagina, tamanio)`, THE `ProductoService` SHALL almacenar el resultado en la región de caché `productos` usando la combinación `{pagina, tamanio}` como clave de caché.

3. WHEN el `ProductoService` recibe una segunda llamada a `getProductos` con los mismos valores de `pagina` y `tamanio`, THE `ProductoService` SHALL retornar el resultado desde la caché sin ejecutar ninguna consulta a la base de datos.

4. THE `CacheManager` SHALL configurar la región `productos` con un TTL de 10 minutos y un tamaño máximo de 100 entradas.

5. WHEN el `ProductoService` ejecuta `crearProducto`, `actualizarProducto` o `eliminarProducto` exitosamente, THE `ProductoService` SHALL invalidar todas las entradas de la región de caché `productos` únicamente después de que la operación se haya completado con éxito; las entradas en caché permanecen válidas mientras la operación de modificación está en curso.

---

### Requirement 8: Caché en memoria para búsqueda de productos

**User Story:** Como usuario, quiero que las búsquedas de productos por término o nombre respondan rápidamente para los mismos parámetros de búsqueda, sin repetir la consulta a la base de datos.

#### Acceptance Criteria

1. WHEN el `ProductoService` ejecuta `buscarProductosPorTermino(termino, pagina, tamanio)`, THE `ProductoService` SHALL almacenar el resultado en la región de caché `busquedaProductos` usando la combinación `{termino, pagina, tamanio}` como clave de caché.

2. WHEN el `ProductoService` ejecuta `buscarProductosPorNombre(nombre, pagina, tamanio)`, THE `ProductoService` SHALL almacenar el resultado en la región de caché `busquedaProductos` usando la combinación `{nombre, pagina, tamanio}` como clave de caché.

3. WHEN el `ProductoService` ejecuta `buscarProductosAvanzado(productoBusquedaDTO)`, THE `ProductoService` SHALL almacenar el resultado en la región de caché `busquedaProductos` usando los campos `{termino, categoriaId, pagina, tamanio}` del DTO como clave de caché.

4. THE `CacheManager` SHALL configurar la región `busquedaProductos` con un TTL de 5 minutos y un tamaño máximo de 200 entradas.

5. WHEN el `ProductoService` ejecuta `crearProducto`, `actualizarProducto` o `eliminarProducto` exitosamente, THE `ProductoService` SHALL invalidar todas las entradas de la región de caché `busquedaProductos` únicamente después de que la operación se haya completado con éxito; las entradas en caché permanecen válidas mientras la operación de modificación está en curso.

---

### Requirement 9: Caché en memoria para detalle de producto por ID

**User Story:** Como usuario, quiero que la consulta del detalle de un producto por su ID responda desde caché en llamadas repetidas, evitando consultas innecesarias a la base de datos.

#### Acceptance Criteria

1. WHEN el `ProductoService` ejecuta `getProductoById(idProducto)`, THE `ProductoService` SHALL almacenar el `ProductoResponseDTO` resultante en la región de caché `productoPorId` usando el `idProducto` como clave de caché.

2. WHEN el `ProductoService` recibe una segunda llamada a `getProductoById` con el mismo `idProducto`, THE `ProductoService` SHALL retornar el resultado desde la caché sin ejecutar ninguna consulta a la base de datos.

3. THE `CacheManager` SHALL configurar la región `productoPorId` con un TTL de 10 minutos y un tamaño máximo de 500 entradas.

4. WHEN el `ProductoService` ejecuta `actualizarProducto(idProducto, ...)` exitosamente, THE `ProductoService` SHALL invalidar únicamente la entrada de la región `productoPorId` correspondiente al `idProducto` actualizado.

5. WHEN el `ProductoService` ejecuta `eliminarProducto(idProducto, ...)` exitosamente, THE `ProductoService` SHALL invalidar únicamente la entrada de la región `productoPorId` correspondiente al `idProducto` eliminado.

---

### Requirement 10: Corrección del problema N+1 en CompraEntity

**User Story:** Como desarrollador, quiero que la carga de compras no genere consultas N+1 al acceder a los detalles, de modo que el rendimiento de los endpoints de compras no se degrade con el volumen de datos.

#### Acceptance Criteria

1. THE `CompraEntity` SHALL cambiar la estrategia de carga de la relación `@OneToMany detalles` de `FetchType.EAGER` a `FetchType.LAZY`, delegando la carga explícita a las consultas JPQL que usen `JOIN FETCH` cuando los detalles sean necesarios.

2. THE `CompraDetalleEntity` SHALL cambiar la estrategia de carga de la relación `@ManyToOne compra` de `FetchType.EAGER` a `FetchType.LAZY`.

3. THE `CompraDetalleEntity` SHALL cambiar la estrategia de carga de la relación `@ManyToOne producto` de `FetchType.EAGER` a `FetchType.LAZY`.

4. WHEN el `CompraRepository` ejecuta `findByIdWithDetails`, THE `CompraRepository` SHALL cargar en una sola consulta SQL la `CompraEntity` junto con sus `detalles`, el `producto` de cada detalle, el `idMetodoPago` y el `usuario`, usando `LEFT JOIN FETCH` explícitos en la query JPQL.

5. WHEN el `CompraRepository` ejecuta las consultas de listado paginado (`findByUsuarioId`, `findAllWithDetails`, `findByFechaBetween`, `findByUsuarioIdAndFechaBetween`), THE `CompraRepository` SHALL separar la consulta de conteo de la consulta de datos usando `@Query(countQuery = ...)` para evitar el warning `HHH90003004` de Hibernate sobre count queries con fetch joins.

---

### Requirement 11: Corrección del problema N+1 en ProductoRepository

**User Story:** Como desarrollador, quiero que las consultas de productos con paginación no generen el warning HHH90003004 de Hibernate ni ejecuten consultas de conteo ineficientes con JOIN FETCH.

#### Acceptance Criteria

1. WHEN el `ProductoRepository` ejecuta `findAllWithDetails` con paginación, THE `ProductoRepository` SHALL definir una `countQuery` separada en la anotación `@Query` que no incluya `JOIN FETCH`, para que Hibernate pueda ejecutar el conteo total de forma eficiente.

2. WHEN el `ProductoRepository` ejecuta `buscarPorNombre`, `buscarPorTermino`, `buscarPorNombreOrdenado` y `buscarPorCategoriaYNombre` con paginación, THE `ProductoRepository` SHALL definir una `countQuery` separada en cada anotación `@Query` que no incluya `JOIN FETCH`.

3. THE `ProductoService` SHALL reemplazar el filtrado en memoria de `buscarProductosAvanzado` cuando solo se proporciona `categoriaId` por una consulta JPQL en `ProductoRepository` que filtre directamente por `categoria.idCategoria` en la cláusula `WHERE`, eliminando la carga de toda la página seguida de un `stream().filter()`.

4. WHEN el `ProductoRepository` ejecuta la nueva consulta de búsqueda por categoría, THE `ProductoRepository` SHALL retornar directamente una `Page<ProductoEntity>` con los productos filtrados por `categoriaId`, sin cargar productos de otras categorías en memoria.

---

### Requirement 12: Configuración de la infraestructura de caché

**User Story:** Como desarrollador, quiero una configuración centralizada y explícita del `CacheManager` con Caffeine, de modo que los TTL y tamaños máximos de cada región de caché estén definidos en un único lugar y sean fáciles de ajustar.

#### Acceptance Criteria

1. THE `CacheManager` SHALL ser configurado mediante una clase `@Configuration` dedicada que declare un bean `CaffeineCacheManager` con las tres regiones: `productos`, `busquedaProductos` y `productoPorId`.

2. THE `CacheManager` SHALL leer los valores de TTL y tamaño máximo de cada región desde las propiedades de la aplicación (`application.properties` o `application.yml`), con valores por defecto definidos en la clase de configuración.

3. WHEN la aplicación arranca, THE `CacheManager` SHALL inicializar las tres regiones de caché con sus configuraciones de Caffeine antes de que cualquier solicitud HTTP sea procesada.

4. WHERE la dependencia `com.github.ben-manes.caffeine:caffeine` no esté presente en el `pom.xml`, THE `CacheManager` SHALL usar `ConcurrentMapCacheManager` como alternativa de caché en memoria, configurando igualmente las tres regiones (`productos`, `busquedaProductos`, `productoPorId`) pero sin TTL configurable, ya que `ConcurrentMapCacheManager` no soporta expiración automática de entradas.

---

### Requirement 13: Rate Limiting en endpoints HTTP

**User Story:** Como operador del sistema, quiero que cada endpoint de la API tenga un límite máximo de peticiones por cliente y por ventana de tiempo, de modo que un cliente abusivo o un ataque de fuerza bruta no pueda saturar el servidor ni degradar el servicio para el resto de usuarios.

#### Acceptance Criteria

1. THE aplicación SHALL implementar Rate Limiting utilizando la librería `bucket4j` con un `ConcurrentHashMap` en memoria como almacén de cubos (buckets), sin dependencia de Redis ni de ningún almacén externo.

2. WHEN un cliente envía una petición HTTP, THE filtro de Rate Limiting SHALL identificar al cliente mediante su dirección IP extraída del header `X-Forwarded-For` o, en su ausencia, desde `HttpServletRequest.getRemoteAddr()`.

3. THE filtro de Rate Limiting SHALL aplicar los siguientes límites por IP y ventana de tiempo:
   - Endpoints de autenticación (`/auth/**`): máximo 10 peticiones por minuto.
   - Endpoints de productos (`/productos/**`): máximo 100 peticiones por minuto.
   - Endpoints de compras (`/compras/**`): máximo 30 peticiones por minuto.
   - Endpoints de usuarios (`/usuarios/**`): máximo 20 peticiones por minuto.

4. WHEN un cliente supera el límite de peticiones permitido para un endpoint, THE filtro de Rate Limiting SHALL retornar una respuesta HTTP 429 (Too Many Requests) con un cuerpo JSON que incluya el mensaje de error y el tiempo restante en segundos hasta que el cubo se recargue.

5. WHEN el filtro de Rate Limiting retorna HTTP 429, THE filtro SHALL incluir el header de respuesta `Retry-After` con el número de segundos que el cliente debe esperar antes de reintentar.

6. WHEN un cliente no ha superado el límite, THE filtro de Rate Limiting SHALL incluir en cada respuesta exitosa los headers `X-RateLimit-Remaining` con el número de tokens disponibles en el cubo y `X-RateLimit-Limit` con el límite total configurado para ese endpoint.

7. THE filtro de Rate Limiting SHALL ser implementado como un `javax.servlet.Filter` (o `jakarta.servlet.Filter`) registrado en el `SecurityConfig` antes del `JwtAuthenticationFilter` en la cadena de filtros, para que las peticiones rechazadas por Rate Limiting no lleguen a la capa de autenticación.

8. WHEN la aplicación arranca, THE filtro de Rate Limiting SHALL inicializar los cubos de forma perezosa (lazy): el cubo de un cliente se crea la primera vez que ese cliente realiza una petición, no en el arranque de la aplicación.

9. IF dos peticiones concurrentes del mismo cliente llegan simultáneamente al filtro, THEN THE filtro de Rate Limiting SHALL garantizar que el acceso al cubo sea thread-safe mediante el uso de `ConcurrentHashMap.computeIfAbsent` o un mecanismo equivalente de atomicidad, evitando condiciones de carrera en la creación o actualización del cubo.


---

### Requirement 14: Índices de base de datos en PostgreSQL

**User Story:** Como desarrollador, quiero que las tablas de PostgreSQL tengan los índices adecuados para las consultas más frecuentes, de modo que el rendimiento de las búsquedas y filtros no degrade con el crecimiento del volumen de datos.

#### Acceptance Criteria

1. THE tabla `compra` SHALL tener un índice en la columna `id_usuario` para acelerar las consultas que filtran compras por usuario (`findByUsuarioId`, `findByUsuarioIdAndNumeroCompra`, `findByUsuarioIdAndFechaBetween`):
   ```sql
   CREATE INDEX idx_compra_id_usuario ON compra(id_usuario);
   ```

2. THE tabla `compra` SHALL tener un índice en la columna `fecha_compra` para acelerar las consultas que filtran compras por rango de fechas (`findByFechaBetween`, `findByUsuarioIdAndFechaBetween`):
   ```sql
   CREATE INDEX idx_compra_fecha_compra ON compra(fecha_compra);
   ```

3. THE tabla `compra` SHALL tener un índice en la columna `compra_estado` para acelerar las consultas que filtran por estado de compra:
   ```sql
   CREATE INDEX idx_compra_estado ON compra(compra_estado);
   ```

4. THE tabla `compra_detalle` SHALL tener un índice en la columna `id_compra` para acelerar las cargas de los detalles de una compra (`findByIdWithDetails` y la relación `@OneToMany detalles`):
   ```sql
   CREATE INDEX idx_compra_detalle_id_compra ON compra_detalle(id_compra);
   ```

5. THE tabla `compra_detalle` SHALL tener un índice en la columna `id_producto` para acelerar las consultas que navegan desde detalles hacia el producto (relación `@ManyToOne producto`) y las operaciones de bloqueo pesimista sobre el stock:
   ```sql
   CREATE INDEX idx_compra_detalle_id_producto ON compra_detalle(id_producto);
   ```

6. THE tabla `producto` SHALL tener un índice en la columna `id_producto_categoria` para acelerar las consultas que filtran productos por categoría (`buscarPorCategoria`, `buscarPorCategoriaYNombre`):
   ```sql
   CREATE INDEX idx_producto_categoria ON producto(id_producto_categoria);
   ```

7. THE tabla `producto` SHALL tener un índice GIN sobre `LOWER(nombre_producto)` con la extensión `pg_trgm` para acelerar las búsquedas por coincidencia parcial con `LIKE` (`buscarPorNombre`, `buscarPorTermino`, `buscarPorNombreOrdenado`):
   ```sql
   CREATE EXTENSION IF NOT EXISTS pg_trgm;
   CREATE INDEX idx_producto_nombre_trgm ON producto USING GIN (LOWER(nombre_producto) gin_trgm_ops);
   ```

8. THE tabla `producto` SHALL tener un índice GIN sobre `LOWER(descripcion_producto)` con `pg_trgm` para acelerar las búsquedas por término en la descripción (`buscarPorTermino`):
   ```sql
   CREATE INDEX idx_producto_descripcion_trgm ON producto USING GIN (LOWER(descripcion_producto) gin_trgm_ops);
   ```

9. THE tabla `usuario` SHALL tener un índice en la columna `email` (aunque ya existe la constraint `UNIQUE`, se debe confirmar que PostgreSQL genera automáticamente el índice subyacente al declarar `unique = true` en la entidad):
   ```sql
   -- Generado automáticamente por la constraint UNIQUE; no requiere CREATE INDEX explícito.
   -- Confirmar con: \d usuario en psql
   ```

10. THE tabla `usuario_codigo_verificacion` SHALL tener un índice en la columna `id_usuario` (FK) para acelerar la búsqueda del código de verificación de un usuario (`findByUsuario`, `deleteByIdUsuario`):
    ```sql
    CREATE INDEX idx_codigo_verificacion_id_usuario ON usuario_codigo_verificacion(id_usuario);
    ```

11. WHEN se aplican los índices anteriores, THE aplicación SHALL registrar los scripts DDL de creación de índices en un archivo de migración de base de datos (por ejemplo, `V2__add_indexes.sql` si se usa Flyway, o como script ejecutable en el arranque), de modo que los índices se apliquen de forma reproducible en cualquier entorno.

---

### Requirement 15: Seguridad en el flujo de recuperación de contraseña

**User Story:** Como usuario, quiero que el endpoint de cambio de contraseña solo pueda ser invocado si previamente completé con éxito la verificación del código de recuperación, de modo que un atacante no pueda cambiar mi contraseña simplemente conociendo mi email, sin haber pasado por la verificación del código.

#### Acceptance Criteria

1. WHEN el usuario invoca `POST /usuario/recuperar/cambiar-contrasena`, THE `UsuarioService` SHALL verificar que existe un `UsuarioCodigoVerificacionEntity` activo asociado al email y que su campo `codigoVerificado` sea `true`; si el campo es `false`, SHALL lanzar una excepción con el mensaje `"Debes verificar el codigo de recuperacion antes de cambiar la contrasena"`.

2. WHEN el usuario completa exitosamente `POST /usuario/recuperar/verificar` con un código correcto y no expirado, THE `UsuarioService` SHALL establecer `codigoVerificado = true` en el `UsuarioCodigoVerificacionEntity` correspondiente y persistir el cambio en la base de datos.

3. WHEN `UsuarioCodigoVerificacionEntity` es creado (al solicitar recuperación o verificación de cuenta), THE entidad SHALL inicializar el campo `codigoVerificado` en `false` como valor por defecto, garantizando que no exista ninguna ventana de tiempo en la que el endpoint de cambio de contraseña sea accesible sin verificación previa.

4. WHEN el `UsuarioService` ejecuta `cambiarContrasenaRecuperacion` exitosamente, THE `UsuarioService` SHALL eliminar el `UsuarioCodigoVerificacionEntity` (incluyendo el flag `codigoVerificado`) mediante `deleteByIdUsuario`, de modo que el token de sesión de recuperación quede invalidado y no pueda reutilizarse.

5. IF el `UsuarioCodigoVerificacionEntity` ha expirado (`expiracion` anterior a `LocalDateTime.now()`) al momento de invocar `cambiarContrasenaRecuperacion`, THEN THE `UsuarioService` SHALL lanzar una excepción con el mensaje `"El proceso de recuperacion ha expirado. Solicita un nuevo codigo"`, independientemente del valor de `codigoVerificado`.

6. THE `UsuarioCodigoVerificacionEntity` SHALL declarar el campo `codigoVerificado` como columna persistida con nombre `codigo_verificado`, tipo booleano no nulo, con valor por defecto `false` a nivel de entidad JPA.

7. WHEN un atacante invoca `POST /usuario/recuperar/cambiar-contrasena` sin haber pasado por `POST /usuario/recuperar/verificar`, THE sistema SHALL retornar una respuesta de error que no revele información sobre el estado interno del código de verificación (si existe o no), más allá del mensaje de que el proceso de recuperación no está activo o no fue verificado.

---

### Requirement 16: Refresh tokens y revocación de sesiones JWT

**User Story:** Como usuario, quiero que cuando mi token de acceso expire pueda renovar mi sesión con un refresh token de larga duración sin tener que volver a ingresar mis credenciales, y quiero que al cerrar sesión mi token quede inmediatamente invalidado aunque no haya expirado.

#### Acceptance Criteria

1. WHEN el usuario inicia sesión exitosamente en `POST /auth/login`, THE `AuthService` SHALL generar dos tokens distintos: un access token JWT firmado con RS256 con expiración máxima de 15 minutos, y un refresh token opaco (UUID v4) con expiración de 7 días; ambos SHALL ser retornados en la respuesta de login.

2. THE sistema SHALL persistir el refresh token en una tabla `refresh_token` de PostgreSQL con los campos: `id` (UUID PK), `token` (VARCHAR UNIQUE), `id_usuario` (FK a `usuario`), `fecha_expiracion` (TIMESTAMP), `revocado` (BOOLEAN DEFAULT FALSE), `fecha_creacion` (TIMESTAMP).

3. WHEN el cliente invoca `POST /auth/refresh` con un refresh token válido en el body o header, THE `AuthService` SHALL verificar que el token existe en la base de datos, que `revocado = false` y que `fecha_expiracion` es posterior a `now()`; si todas las condiciones se cumplen, SHALL emitir un nuevo access token JWT y un nuevo refresh token (rotación), revocar el refresh token anterior, y retornar los nuevos tokens.

4. IF el refresh token no existe, está revocado o ha expirado al invocar `POST /auth/refresh`, THEN THE `AuthService` SHALL retornar HTTP 401 con mensaje `"Refresh token inválido o expirado"` sin revelar cuál condición falló.

5. WHEN el usuario invoca `POST /auth/logout` con su access token vigente, THE `AuthService` SHALL revocar el refresh token asociado al usuario (`revocado = true`) y agregar el `jti` del access token a una blacklist en memoria (Caffeine, TTL igual al tiempo de expiración restante del token) para que el access token sea rechazado aunque no haya expirado.

6. WHEN el `JwtAuthenticationFilter` valida un access token, THE filtro SHALL consultar la blacklist en memoria y rechazar con HTTP 401 cualquier token cuyo `jti` esté en la lista, independientemente de si la firma y la expiración son válidas.

7. THE access token SHALL tener una expiración máxima de 15 minutos (configurable vía `jwt.expiration` con un valor por defecto de `900000` ms); cualquier valor en `application.properties` superior a 15 minutos SHALL ser rechazado en el arranque de la aplicación con un error de configuración explícito.

8. IF un usuario con refresh token activo inicia sesión nuevamente (emitir nuevo par de tokens), THEN THE `AuthService` SHALL revocar todos los refresh tokens anteriores del usuario antes de crear el nuevo, evitando acumulación de tokens activos por usuario.

---

### Requirement 17: Forzar HTTPS y proteger las claves PEM

**User Story:** Como operador del sistema, quiero que toda la comunicación con la API ocurra exclusivamente por HTTPS y que los archivos de clave privada RSA nunca queden expuestos en el repositorio de código ni en rutas accesibles por el servidor web.

#### Acceptance Criteria

1. WHERE la aplicación se ejecuta en un perfil de producción (`spring.profiles.active=prod`), THE `SecurityConfig` SHALL agregar `.requiresChannel(channel -> channel.anyRequest().requiresSecure())` para que cualquier petición HTTP sin TLS sea redirigida automáticamente a HTTPS con código HTTP 301.

2. THE archivo `private_key.pem` SHALL estar listado explícitamente en `.gitignore` para que nunca sea comprometido en el repositorio de código; se verificará que la entrada `*.pem` o `private_key.pem` existe en `.gitignore`.

3. THE propiedad `jwt.private-key-location` SHALL soportar rutas externas al classpath (prefijo `file:`) en producción, de modo que el archivo `private_key.pem` pueda almacenarse fuera del directorio de la aplicación y con permisos de lectura restringidos al usuario del proceso.

4. WHEN la aplicación arranca con el perfil `prod`, THE `JwtService` SHALL verificar que el tamaño de la clave RSA es de al menos 2048 bits; si la clave tiene menos bits, SHALL lanzar una excepción en `@PostConstruct` con el mensaje `"La clave RSA debe tener al menos 2048 bits en producción"` e impedir el arranque.

5. THE header CORS `Access-Control-Allow-Origin` SHALL permitir únicamente orígenes con protocolo `https://` en producción; cualquier origen que empiece con `http://` (sin TLS) SHALL ser rechazado con HTTP 403 cuando el perfil activo sea `prod`.

6. THE archivo `.env.example` SHALL mostrar `CORS_ALLOWED_ORIGINS=https://tu-dominio.com` como valor de ejemplo para el origen CORS en producción, eliminando la referencia a `http://localhost:8080` que podría inducir a configurar orígenes inseguros en producción.

---

### Requirement 18: Validación de expiración configurable del access token

**User Story:** Como desarrollador, quiero que el tiempo de expiración del access token tenga un límite máximo configurable y verificado en el arranque, de modo que un error de configuración no genere tokens de larga duración que amplíen la ventana de ataque.

#### Acceptance Criteria

1. THE `JwtService` SHALL leer la propiedad `jwt.expiration` como milisegundos y validar en `@PostConstruct` que el valor no supere los 900 000 ms (15 minutos); si el valor configurado es mayor, SHALL lanzar una `IllegalStateException` con el mensaje `"jwt.expiration no puede superar 15 minutos (900000 ms)"` para que la aplicación no arranque con tokens de larga duración.

2. THE `application.properties` SHALL declarar `jwt.expiration=900000` (15 minutos) como valor por defecto explícito, con un comentario que indique el máximo permitido.

3. WHEN el `JwtService` genera un access token, THE token SHALL incluir el claim `jti` con un UUID v4 único por token (ya implementado), y el claim `exp` SHALL corresponder exactamente a `iat + jwt.expiration` milisegundos, sin tolerancia adicional más allá de los 30 segundos de `clockSkewSeconds` definidos en el parser.

4. IF la propiedad `jwt.expiration` no está definida en `application.properties`, THEN THE `JwtService` SHALL usar un valor por defecto de 900 000 ms (15 minutos) mediante la anotación `@Value("${jwt.expiration:900000}")`, garantizando que la aplicación arranque de forma segura sin configuración explícita.

---

### Requirement 19: Integración con la pasarela de pagos Wompi

**User Story:** Como cliente, quiero poder pagar mis compras usando Bancolombia Transfer, Nequi o tarjeta débito/crédito Mastercard a través de Wompi, de modo que el sistema procese el pago de forma segura y me confirme el resultado.

#### Acceptance Criteria

1. WHEN el cliente confirma una compra con `POST /compras/realizar`, THE `CompraService` SHALL iniciar una transacción en Wompi usando las credenciales de la llave pública configurada (`wompi.public-key`), enviando el monto en centavos, la referencia única de la compra (`numeroCompra`) y la moneda `COP`.

2. THE sistema SHALL soportar exactamente tres métodos de pago de Wompi: `BANCOLOMBIA_TRANSFER`, `NEQUI` y `CARD`; cualquier otro valor SHALL ser rechazado con HTTP 400 antes de llamar a la API de Wompi.

3. WHEN el cliente selecciona `BANCOLOMBIA_TRANSFER`, THE `WompiService` SHALL construir el objeto `payment_method` con `type: "BANCOLOMBIA_TRANSFER"` y el `sandbox_status` o datos reales según el perfil activo, y retornar al cliente la URL de redirección (`async_payment_url`) para completar el pago en Bancolombia.

4. WHEN el cliente selecciona `NEQUI`, THE `WompiService` SHALL construir el objeto `payment_method` con `type: "NEQUI"` y el número de teléfono celular del cliente, y enviar la notificación push de pago a Nequi.

5. WHEN el cliente selecciona `CARD`, THE `WompiService` SHALL construir el objeto `payment_method` con `type: "CARD"` usando el `token` de tarjeta previamente tokenizado por Wompi.js en el frontend, sin que los datos de la tarjeta pasen por el backend del servidor.

6. THE `WompiService` SHALL usar las propiedades `wompi.public-key` y `wompi.private-key` inyectadas vía `@Value` desde `application.properties` (o variables de entorno), con los valores del sandbox para el perfil `dev` y los valores de producción para el perfil `prod`.

7. WHEN Wompi retorna una transacción con `status: "APPROVED"`, THE `CompraService` SHALL actualizar el estado de la `CompraEntity` a `ACEPTADO` y almacenar el `id` de la transacción Wompi en el campo `wompiTransaccionId` de `CompraEntity`.

8. WHEN Wompi retorna una transacción con `status: "DECLINED"` o `"ERROR"`, THE `CompraService` SHALL revertir la compra completa (rollback de stock) y retornar HTTP 402 con un mensaje de error que describa el motivo del rechazo proporcionado por Wompi.

9. WHEN Wompi retorna una transacción con `status: "PENDING"`, THE `CompraService` SHALL guardar la compra en estado `PENDIENTE` y retornar al cliente el `id` de la transacción Wompi para que pueda consultar el estado posteriormente.

---

### Requirement 20: Webhook de Wompi para actualización de estado de pago

**User Story:** Como operador del sistema, quiero que cuando Wompi notifique el resultado de un pago asíncrono (Bancolombia Transfer, Nequi) a través de su webhook, el sistema actualice el estado de la compra automáticamente sin intervención manual.

#### Acceptance Criteria

1. THE sistema SHALL exponer el endpoint `POST /pagos/wompi/webhook` como ruta pública (sin autenticación JWT) para recibir las notificaciones de Wompi.

2. WHEN Wompi envía una notificación al webhook, THE `WompiWebhookService` SHALL verificar la autenticidad de la notificación comprobando la firma `x-event-checksum` del header HTTP contra el hash `SHA256(id_evento + timestamp + wompi.events-key)` calculado con la llave de eventos (`wompi.events-key`); si la firma no coincide, SHALL retornar HTTP 401 sin procesar el evento.

3. WHEN la firma del webhook es válida y el evento es `transaction.updated` con `status: "APPROVED"`, THE `WompiWebhookService` SHALL buscar la `CompraEntity` por el campo `wompiTransaccionId`, actualizar su estado a `ACEPTADO` y persistir el cambio en una transacción `@Transactional`.

4. WHEN la firma del webhook es válida y el evento es `transaction.updated` con `status: "DECLINED"` o `"VOIDED"`, THE `WompiWebhookService` SHALL buscar la `CompraEntity` por `wompiTransaccionId`, restaurar el stock de todos los productos del detalle (usando `findByIdWithLock`) y actualizar el estado a `CANCELADO`, todo en una única transacción `REPEATABLE_READ`.

5. IF el webhook recibe un evento duplicado (mismo `id_evento` ya procesado), THEN THE `WompiWebhookService` SHALL retornar HTTP 200 sin re-procesar el evento, usando el `IdempotencyStore` con el `id_evento` como clave.

6. THE endpoint del webhook SHALL retornar HTTP 200 siempre que la firma sea válida, independientemente del resultado del procesamiento interno, para evitar que Wompi reintente la notificación indefinidamente por errores internos del sistema.

---

### Requirement 21: Tokenización segura de tarjetas con Wompi.js

**User Story:** Como cliente, quiero poder pagar con tarjeta débito o crédito sin que mis datos de tarjeta sean enviados al servidor de la tienda, garantizando que solo el token seguro de Wompi sea procesado por el backend.

#### Acceptance Criteria

1. THE backend SHALL nunca recibir, almacenar ni registrar en logs los datos sensibles de la tarjeta (número, CVV, fecha de vencimiento); únicamente SHALL aceptar el `token` de tarjeta generado por Wompi.js en el frontend.

2. WHEN el cliente envía `POST /compras/realizar` con método de pago `CARD`, THE `CompraRequestDTO` SHALL incluir el campo `wompiCardToken` (String, obligatorio si el método es `CARD`) y el campo `cuotas` (Integer, valor entre 1 y 36, por defecto 1).

3. WHEN el `WompiService` construye la transacción de tipo `CARD`, THE servicio SHALL incluir en `payment_method` los campos `token` (el `wompiCardToken` del cliente) e `installments` (el valor de `cuotas`), y nunca agregar datos crudos de tarjeta.

4. IF el `wompiCardToken` es nulo o vacío cuando el método de pago es `CARD`, THEN THE `CompraController` SHALL retornar HTTP 400 con el mensaje `"El token de tarjeta es obligatorio para pagos con tarjeta"` antes de llamar al servicio.

5. THE `WompiService` SHALL configurar la llamada a la API de Wompi con un timeout de conexión de 5 segundos y un timeout de lectura de 15 segundos, lanzando `WompiTimeoutException` si se supera cualquiera de los dos tiempos, lo que desencadenará el rollback de la compra.

---

### Requirement 22: Configuración segura de credenciales Wompi

**User Story:** Como desarrollador, quiero que las credenciales de Wompi estén externalizadas en variables de entorno y nunca sean incluidas en el repositorio de código, de modo que el sandbox y la producción usen llaves distintas sin riesgo de exposición.

#### Acceptance Criteria

1. THE aplicación SHALL leer las cuatro credenciales de Wompi exclusivamente desde propiedades de entorno: `WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY`, `WOMPI_EVENTS_KEY` e `WOMPI_INTEGRITY_KEY`; ninguna de estas llaves SHALL tener valor hardcodeado en el código fuente.

2. THE `application.properties` SHALL declarar las cuatro propiedades de Wompi como referencias a variables de entorno sin valores por defecto: `wompi.public-key=${WOMPI_PUBLIC_KEY}`, `wompi.private-key=${WOMPI_PRIVATE_KEY}`, `wompi.events-key=${WOMPI_EVENTS_KEY}` e `wompi.integrity-key=${WOMPI_INTEGRITY_KEY}`.

3. THE `.env.example` SHALL incluir las cuatro variables de Wompi con valores de placeholder que indiquen claramente que deben reemplazarse: `WOMPI_PUBLIC_KEY=pub_stagtest_...`, `WOMPI_PRIVATE_KEY=prv_stagtest_...`, etc.

4. THE `.gitignore` SHALL confirmar que el archivo `.env` (que contiene las llaves reales) está excluido del control de versiones.

5. WHEN la aplicación arranca y alguna de las cuatro propiedades de Wompi es nula o vacía, THE `WompiConfig` SHALL lanzar una `IllegalStateException` en `@PostConstruct` con el mensaje `"Las credenciales de Wompi no están configuradas"` para evitar que la aplicación funcione sin llaves válidas.

---

### Requirement 23: Consulta de estado de transacción Wompi

**User Story:** Como cliente, quiero poder consultar el estado actual de una transacción Wompi para saber si mi pago asíncrono (Bancolombia Transfer o Nequi) fue procesado, sin tener que esperar una notificación push.

#### Acceptance Criteria

1. THE sistema SHALL exponer el endpoint `GET /compras/{compraId}/pago/estado` (autenticado, rol `CLIENTE` o `ADMIN`) que retorne el estado actual de la transacción Wompi asociada a la compra.

2. WHEN el `WompiService` consulta el estado de una transacción, THE servicio SHALL llamar a `GET https://sandbox.wompi.co/v1/transactions/{wompiTransaccionId}` (o el endpoint de producción según el perfil), usando la llave privada como bearer token.

3. WHEN la transacción consultada tiene `status: "APPROVED"` y la `CompraEntity` aún está en `PENDIENTE`, THE `WompiService` SHALL actualizar el estado de la compra a `ACEPTADO` como efecto secundario de la consulta y retornar el nuevo estado al cliente.

4. THE respuesta del endpoint SHALL incluir los campos: `compraId`, `numeroCompra`, `estadoCompra`, `wompiTransaccionId`, `estadoWompi` y `fechaActualizacion`.

5. IF la `CompraEntity` no tiene `wompiTransaccionId` (pago no iniciado), THEN THE `WompiService` SHALL retornar HTTP 404 con el mensaje `"No existe una transacción Wompi asociada a esta compra"`.
