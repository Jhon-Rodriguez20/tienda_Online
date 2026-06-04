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

