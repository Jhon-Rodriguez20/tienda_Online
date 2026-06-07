# Implementation Plan: transacciones-cache-optimizacion

## Overview

Implementación de cinco mejoras ortogonales sobre la tienda online Spring Boot 4 / Java 25 / PostgreSQL:
1. **Idempotencia** en `POST /compras/realizar` y `PUT /compras/admin/{compraId}/estado` mediante el header `Idempotency-Key`.
2. **Refuerzo ACID** en `CompraService` y `ProductoService` (aislamiento `REPEATABLE_READ`, bloqueo pesimista, `saveAll` post-loop, `@Transactional(readOnly=true)`).
3. **Caché en memoria con Caffeine** para las regiones `productos`, `busquedaProductos` y `productoPorId`.
4. **Corrección N+1** en entidades JPA, repositorios y servicio.
5. **Rate Limiting** con `bucket4j` en endpoints HTTP, aplicando límites por IP y endpoint antes de la autenticación.

Las cinco mejoras son independientes y no requieren cambios de esquema de base de datos.

---

## Tasks

- [x] 1. Agregar dependencias y configuración base
  - [x] 1.1 Agregar dependencias de Caffeine, Spring Cache y jqwik en `pom.xml`
    - Agregar `spring-boot-starter-cache` (sin versión, gestionada por Spring Boot BOM)
    - Agregar `com.github.ben-manes.caffeine:caffeine:3.1.8`
    - Agregar `net.jqwik:jqwik:1.8.4` con `scope=test`
    - _Requirements: 7.1, 12.1_

  - [x] 1.2 Agregar propiedades de caché e idempotencia en `application.properties`
    - Agregar las seis propiedades de caché (`cache.productos.*`, `cache.busqueda-productos.*`, `cache.producto-por-id.*`)
    - Agregar las dos propiedades del `IdempotencyStore` (`idempotency.ttl-hours`, `idempotency.max-size`)
    - _Requirements: 12.2_

- [x] 2. Implementar infraestructura de caché
  - [x] 2.1 Crear `CacheConfig.java` con `CaffeineCacheManager` y las tres regiones
    - Crear clase `@Configuration @EnableCaching` en el paquete `config`
    - Inyectar los seis valores de TTL y tamaño máximo con `@Value` y valores por defecto
    - Declarar bean `CacheManager` con `CaffeineCacheManager`, registrar las tres regiones (`productos`, `busquedaProductos`, `productoPorId`) cada una con su propio `Caffeine` spec (TTL + maximumSize)
    - _Requirements: 12.1, 12.2, 12.3, 7.4, 8.4, 9.3_

  - [x] 2.2 Agregar `@EnableCaching` en `TiendaOnlineApplication.java`
    - Añadir la anotación `@EnableCaching` a la clase principal (o confirmar que ya está en `CacheConfig`)
    - _Requirements: 12.3_

- [x] 3. Implementar `IdempotencyStore`
  - [x] 3.1 Crear `IdempotencyStore.java` como componente Spring
    - Crear clase `@Component` en el paquete `service` (o subpaquete `idempotency`)
    - Construir el `Cache<String, CompraResponseDTO>` de Caffeine con `expireAfterWrite(ttlHours)` y `maximumSize(maxSize)` inyectados vía `@Value`
    - Implementar métodos `get(String key)`, `put(String key, CompraResponseDTO response)` y `contains(String key)`
    - Usar `Cache.getIfPresent` para `get` y `contains`; usar `Cache.put` para `put`
    - _Requirements: 1.2, 1.3, 1.6, 2.2, 2.3_

  - [x] 3.2 Crear el record `IdempotencyResult<T>`
    - Crear `IdempotencyResult.java` como `public record IdempotencyResult<T>(T data, boolean replayed) {}`
    - Ubicar en el paquete `model.dto` o `service`
    - _Requirements: 1.7, 2.1_

- [x] 4. Modificar `CompraController` para idempotencia
  - [x] 4.1 Agregar parámetro `Idempotency-Key` y validaciones en `realizarCompra`
    - Agregar `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey` al método `realizarCompra`
    - Implementar validación de presencia (null/blank → HTTP 400 con mensaje `"El header Idempotency-Key es obligatorio"`)
    - Implementar validación de formato UUID v4 con constante estática `UUID_V4_PATTERN` (regex `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)
    - Leer el flag `replayed` del `IdempotencyResult` retornado por el servicio y añadir header `Idempotency-Replayed: true` si aplica
    - Actualizar la firma de la llamada a `compraService.realizarCompra` para pasar `idempotencyKey`
    - _Requirements: 1.1, 1.4, 1.5, 1.7_

  - [x] 4.2 Agregar parámetro `Idempotency-Key` y validaciones en `actualizarEstadoCompra`
    - Agregar `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey` al método `actualizarEstadoCompra`
    - Reutilizar la misma lógica de validación de presencia y formato UUID v4
    - Leer el flag `replayed` del `IdempotencyResult` y añadir header `Idempotency-Replayed: true` si aplica
    - Actualizar la firma de la llamada a `compraService.putEstadoCompra` para pasar `idempotencyKey`
    - _Requirements: 2.1, 2.4_

- [x] 5. Modificar `CompraService` — idempotencia y refuerzo ACID
  - [x] 5.1 Refactorizar `realizarCompra` con idempotencia, `REPEATABLE_READ` y `saveAll`
    - Cambiar firma a `realizarCompra(CompraRequestDTO request, UUID usuarioId, String idempotencyKey)`
    - Agregar `@Transactional(isolation = Isolation.REPEATABLE_READ)` (reemplazar la anotación existente)
    - Al inicio del método: consultar `idempotencyStore.get(idempotencyKey)`; si existe, retornar `new IdempotencyResult<>(cached, true)`
    - Reemplazar `productoRepository.findById` dentro del loop por `productoRepository.findByIdWithLock` (bloqueo pesimista)
    - Acumular productos modificados en `Map<UUID, ProductoEntity> modifiedProducts = new LinkedHashMap<>()`
    - Eliminar `productoRepository.save(producto)` dentro del loop
    - Agregar `productoRepository.saveAll(modifiedProducts.values())` una sola vez después del loop
    - Al final: `idempotencyStore.put(idempotencyKey, response)`; retornar `new IdempotencyResult<>(response, false)`
    - _Requirements: 1.2, 1.3, 3.1, 3.2, 3.4, 5.1, 5.2_

  - [x] 5.2 Refactorizar `cancelarCompra` con `REPEATABLE_READ` y `saveAll`
    - Agregar `@Transactional(isolation = Isolation.REPEATABLE_READ)` (reemplazar la anotación existente)
    - Reemplazar `productoRepository.findById` (implícito en `detalle.getProducto()`) por `productoRepository.findByIdWithLock(detalle.getProducto().getIdProducto())`
    - Acumular productos restaurados en `Map<UUID, ProductoEntity> restoredProducts = new LinkedHashMap<>()`
    - Eliminar `productoRepository.save(productoEntity)` dentro del loop
    - Agregar `productoRepository.saveAll(restoredProducts.values())` una sola vez después del loop
    - _Requirements: 4.1, 4.2, 5.3_

  - [x] 5.3 Agregar idempotencia en `putEstadoCompra`
    - Cambiar firma a `putEstadoCompra(UUID compraId, ActualizarEstadoCompraDTO request, UUID adminId, String idempotencyKey)`
    - Al inicio: consultar `idempotencyStore.get(idempotencyKey)`; si existe, retornar `new IdempotencyResult<>(cached, true)`
    - Al final: `idempotencyStore.put(idempotencyKey, response)`; retornar `new IdempotencyResult<>(response, false)`
    - _Requirements: 2.2, 2.3_

  - [x] 5.4 Anotar métodos de consulta con `@Transactional(readOnly = true)` en `CompraService`
    - Agregar `@Transactional(readOnly = true)` a `getMisCompras`, `getCompraById` y `getAllCompras`
    - _Requirements: 6.1_

- [x] 6. Checkpoint — Verificar idempotencia y ACID
  - Asegurarse de que todos los tests de los pasos 5 y 6 pasan y el proyecto compila sin errores. Consultar al usuario si surgen dudas.

- [x] 7. Modificar `ProductoRepository` — corrección N+1 y bloqueo pesimista
  - [x] 7.1 Agregar `countQuery` a todas las queries paginadas en `ProductoRepository`
    - Agregar `countQuery = "SELECT COUNT(p) FROM ProductoEntity p"` a `findAllWithDetails`
    - Agregar `countQuery` sin `JOIN FETCH` a `buscarPorNombre`, `buscarPorTermino`, `buscarPorNombreOrdenado` y `buscarPorCategoriaYNombre`
    - _Requirements: 11.1, 11.2_

  - [x] 7.2 Agregar método `buscarPorCategoria` en `ProductoRepository`
    - Declarar `Page<ProductoEntity> buscarPorCategoria(@Param("categoriaId") UUID categoriaId, Pageable pageable)`
    - Usar `@Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario WHERE p.categoria.idCategoria = :categoriaId", countQuery = "SELECT COUNT(p) FROM ProductoEntity p WHERE p.categoria.idCategoria = :categoriaId")`
    - _Requirements: 11.3, 11.4_

  - [x] 7.3 Agregar método `findByIdWithLock` en `ProductoRepository`
    - Declarar `Optional<ProductoEntity> findByIdWithLock(@Param("id") UUID id)`
    - Anotar con `@Lock(LockModeType.PESSIMISTIC_WRITE)` y `@Query("SELECT p FROM ProductoEntity p WHERE p.idProducto = :id")`
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 8. Modificar `CompraRepository` — corrección N+1
  - [x] 8.1 Agregar `countQuery` a las queries paginadas en `CompraRepository`
    - Agregar `countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c WHERE c.usuario.idUsuario = :usuarioId"` a `findByUsuarioId`
    - Agregar `countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c"` a `findAllWithDetails`
    - Agregar `countQuery` apropiado a `findByFechaBetween` y `findByUsuarioIdAndFechaBetween`
    - _Requirements: 10.5_

- [x] 9. Modificar entidades JPA — corrección N+1
  - [x] 9.1 Cambiar `FetchType.EAGER` a `FetchType.LAZY` en `CompraEntity`
    - En `CompraEntity`, cambiar la relación `@OneToMany detalles` de `fetch = FetchType.EAGER` a `fetch = FetchType.LAZY`
    - _Requirements: 10.1_

  - [x] 9.2 Cambiar `FetchType.EAGER` a `FetchType.LAZY` en `CompraDetalleEntity`
    - En `CompraDetalleEntity`, cambiar `@ManyToOne compra` de `FetchType.EAGER` a `FetchType.LAZY`
    - En `CompraDetalleEntity`, cambiar `@ManyToOne producto` de `FetchType.EAGER` a `FetchType.LAZY`
    - _Requirements: 10.2, 10.3_

- [x] 10. Checkpoint — Verificar corrección N+1
  - Asegurarse de que los tests de los pasos 8, 9 y 10 pasan y que el proyecto compila. Verificar que `findByIdWithDetails` en `CompraRepository` ya incluye los `LEFT JOIN FETCH` necesarios para cargar detalles, producto, método de pago y usuario. Consultar al usuario si surgen dudas.

- [x] 11. Modificar `ProductoService` — caché y corrección N+1
  - [x] 11.1 Agregar `@Cacheable` y `@Transactional(readOnly=true)` a los métodos de consulta
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "productos", key = "#pagina + '-' + #tamanio")` a `getProductos`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#termino + '-' + #pagina + '-' + #tamanio")` a `buscarProductosPorTermino`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#nombre + '-' + #pagina + '-' + #tamanio")` a `buscarProductosPorNombre`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#productoBusquedaDTO.termino + '-' + #productoBusquedaDTO.categoriaId + '-' + #productoBusquedaDTO.pagina + '-' + #productoBusquedaDTO.tamanio")` a `buscarProductosAvanzado`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "productoPorId", key = "#idProducto")` a `getProductoById`
    - _Requirements: 6.2, 7.2, 7.3, 8.1, 8.2, 8.3, 9.1, 9.2_

  - [x] 11.2 Reemplazar filtro en memoria en `buscarProductosAvanzado` por consulta a BD
    - En la rama `else if (categoriaIdOpt.isPresent())` de `buscarProductosAvanzado`, reemplazar `productoRepository.findAllWithDetails(pageable)` + `stream().filter()` por `productoRepository.buscarPorCategoria(categoriaIdOpt.get(), pageable)`
    - Eliminar la construcción del `PageImpl` manual con la lista filtrada
    - _Requirements: 11.3, 11.4_

  - [x] 11.3 Agregar `@CacheEvict` a los métodos de modificación en `ProductoService`
    - En `crearProducto`: agregar `@CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true)`
    - En `actualizarProducto`: agregar `@Caching(evict = { @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true), @CacheEvict(value = "productoPorId", key = "#idProducto") })`
    - En `eliminarProducto`: agregar `@Caching(evict = { @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true), @CacheEvict(value = "productoPorId", key = "#idProducto") })`
    - _Requirements: 7.5, 8.5, 9.4, 9.5_

- [x] 12. Implementar infraestructura de Rate Limiting
  - [x] 12.1 Agregar dependencia de bucket4j en `pom.xml`
    - Agregar `com.bucket4j:bucket4j-core:8.10.1` en la sección `<dependencies>`
    - _Requirements: 13.1_

  - [x] 12.2 Agregar propiedades de rate limiting en `application.properties`
    - Agregar las ocho propiedades de rate limiting (`rate-limit.auth.requests`, `rate-limit.auth.duration-minutes`, `rate-limit.productos.requests`, `rate-limit.productos.duration-minutes`, `rate-limit.compras.requests`, `rate-limit.compras.duration-minutes`, `rate-limit.usuarios.requests`, `rate-limit.usuarios.duration-minutes`)
    - _Requirements: 13.3_

  - [x] 12.3 Crear `RateLimitingFilter.java` con lógica de rate limiting
    - Crear clase `@Component` que extiende `OncePerRequestFilter` en el paquete `config`
    - Inyectar los ocho valores de límites por endpoint con `@Value` y valores por defecto
    - Declarar `ConcurrentHashMap<String, Bucket> buckets` como campo de instancia
    - Implementar `doFilterInternal`: extraer IP del cliente (`extractClientIp`), determinar endpoint (`determineEndpoint`), construir clave bucket (`clientIp + ":" + endpoint`), obtener o crear bucket con `computeIfAbsent`, intentar consumir 1 token con `tryConsumeAndReturnRemaining`
    - Si `probe.isConsumed()` es true: agregar headers `X-RateLimit-Remaining` y `X-RateLimit-Limit`, continuar la cadena de filtros
    - Si `probe.isConsumed()` es false: calcular `waitForRefill` en segundos, retornar HTTP 429 con header `Retry-After` y cuerpo JSON `{"error": "Too many requests", "retryAfterSeconds": ...}`
    - Implementar `extractClientIp(HttpServletRequest)`: leer header `X-Forwarded-For`, si presente extraer primera IP con `split(",")[0].trim()`, si ausente retornar `request.getRemoteAddr()`
    - Implementar `determineEndpoint(String uri)`: mapear prefijos `/auth/`, `/productos/`, `/compras/`, `/usuarios/` a sus respectivos nombres, fallback a `"default"`
    - Implementar `createBucket(String endpoint)`: crear `Bandwidth` con `Bandwidth.simple(requests, Duration.ofMinutes(duration))` según el endpoint, retornar `Bucket.builder().addLimit(limit).build()`
    - Implementar `getLimitForEndpoint(String endpoint)`: retornar el límite numérico configurado para el endpoint
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.8, 13.9_

  - [x] 12.4 Modificar `SecurityConfig.java` para registrar `RateLimitingFilter` antes de `JwtAuthenticationFilter`
    - Inyectar `RateLimitingFilter` en el constructor de `SecurityConfig`
    - En el método `securityFilterChain`, agregar `.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)` ANTES de la línea que registra `jwtAuthenticationFilter`
    - _Requirements: 13.7_

- [x] 13. Checkpoint — Verificar rate limiting
  - Asegurarse de que todos los tests del paso 12 pasan y el proyecto compila sin errores. Verificar que el filtro se aplica correctamente antes de la autenticación y que los límites por endpoint funcionan de forma independiente. Consultar al usuario si surgen dudas.

- [x] 14. Tests de propiedades para rate limiting
  - [x] 14.1 Escribir property test para extracción correcta de IP del cliente
    - **Property 15: Extracción correcta de IP del cliente**
    - **Validates: Requirements 13.2**
    - Crear `RateLimitingFilterPropertyTest.java` en el paquete de test `config`
    - Usar `@Property` con jqwik para generar valores arbitrarios de IPs (con y sin `X-Forwarded-For`)
    - Verificar que cuando `X-Forwarded-For` está presente, se extrae la primera IP; cuando ausente, se usa `RemoteAddr`

  - [x] 14.2 Escribir property test para HTTP 429 cuando se supera el límite
    - **Property 16: HTTP 429 cuando se supera el límite de rate limiting**
    - **Validates: Requirements 13.4**
    - Usar `@Property` para generar límites arbitrarios `L` y número de peticiones `L + N` (donde `N > 0`)
    - Verificar que todas las peticiones después de la `L`-ésima reciben HTTP 429 con cuerpo JSON correcto

  - [x] 14.3 Escribir property test para header Retry-After en respuestas HTTP 429
    - **Property 17: Header Retry-After en respuestas HTTP 429**
    - **Validates: Requirements 13.5**
    - Usar `@Property` para generar escenarios donde el límite se supera
    - Verificar que todas las respuestas HTTP 429 incluyen el header `Retry-After` con un valor numérico válido

  - [x] 14.4 Escribir property test para headers informativos en respuestas exitosas
    - **Property 18: Headers informativos en respuestas exitosas de rate limiting**
    - **Validates: Requirements 13.6**
    - Usar `@Property` para generar peticiones que no superan el límite
    - Verificar que las respuestas incluyen `X-RateLimit-Remaining` y `X-RateLimit-Limit` con valores correctos

  - [x] 14.5 Escribir property test para inicialización perezosa de buckets
    - **Property 19: Inicialización perezosa de buckets de rate limiting**
    - **Validates: Requirements 13.8**
    - Usar `@Property` para generar IPs de clientes nuevos
    - Verificar que el bucket no existe antes de la primera petición y existe después

  - [x] 14.6 Escribir property test para thread-safety en creación y consumo de buckets
    - **Property 20: Thread-safety en creación y consumo de buckets**
    - **Validates: Requirements 13.9**
    - Usar `@Property` para generar `N` peticiones concurrentes del mismo cliente
    - Verificar que exactamente `N` tokens se consumen, solo existe un bucket para la clave, y no hay condiciones de carrera

- [x] 15. Tests unitarios e integración para rate limiting
  - [x] 15.1 Escribir tests unitarios para límites por endpoint
    - Crear `RateLimitingFilterTest.java` en el paquete de test `config`
    - Test para endpoint `/auth/**`: enviar 10 peticiones exitosas, petición 11 → HTTP 429
    - Test para endpoint `/productos/**`: enviar 100 peticiones exitosas, petición 101 → HTTP 429
    - Test para endpoint `/compras/**`: enviar 30 peticiones exitosas, petición 31 → HTTP 429
    - Test para endpoint `/usuarios/**`: enviar 20 peticiones exitosas, petición 21 → HTTP 429
    - _Requirements: 13.3, 13.4_

  - [x] 15.2 Escribir tests unitarios para extracción de IP y headers de respuesta
    - Test para petición con `X-Forwarded-For: 192.168.1.1, 10.0.0.1` → extrae `192.168.1.1`
    - Test para petición sin `X-Forwarded-For` → usa `RemoteAddr`
    - Test para respuesta HTTP 429 → incluye header `Retry-After` y cuerpo JSON con `retryAfterSeconds`
    - Test para respuestas exitosas → incluyen headers `X-RateLimit-Remaining` y `X-RateLimit-Limit`
    - _Requirements: 13.2, 13.5, 13.6_

  - [x] 15.3 Escribir tests de integración para aislamiento de límites entre clientes y endpoints
    - Crear `RateLimitingFilterIntegrationTest.java` en el paquete de test `config`
    - Test para dos clientes con IPs diferentes accediendo al mismo endpoint → cada cliente tiene su propio límite independiente
    - Test para un cliente agotando el límite en un endpoint → puede seguir accediendo a otros endpoints sin restricción
    - Test para cliente esperando el tiempo indicado en `Retry-After` → puede volver a hacer peticiones exitosamente
    - _Requirements: 13.3, 13.4, 13.5, 13.7_

- [ ] 16. Crear script de índices de base de datos PostgreSQL
  - [ ] 16.1 Habilitar la extensión `pg_trgm` y crear todos los índices en un script DDL
    - Crear el archivo `src/main/resources/db/migration/V2__add_indexes.sql` (o el script equivalente si no se usa Flyway)
    - Agregar `CREATE EXTENSION IF NOT EXISTS pg_trgm;` al inicio del script
    - Crear `idx_compra_id_usuario` en `compra(id_usuario)` — optimiza `findByUsuarioId`, `findByUsuarioIdAndNumeroCompra`, `findByUsuarioIdAndFechaBetween`
    - Crear `idx_compra_fecha_compra` en `compra(fecha_compra)` — optimiza `findByFechaBetween` y `findByUsuarioIdAndFechaBetween`
    - Crear `idx_compra_estado` en `compra(compra_estado)` — optimiza filtros por estado de compra
    - Crear `idx_compra_detalle_id_compra` en `compra_detalle(id_compra)` — optimiza la carga de la relación `@OneToMany detalles`
    - Crear `idx_compra_detalle_id_producto` en `compra_detalle(id_producto)` — optimiza la navegación `@ManyToOne producto` y el bloqueo pesimista
    - Crear `idx_producto_categoria` en `producto(id_producto_categoria)` — optimiza `buscarPorCategoria` y `buscarPorCategoriaYNombre`
    - Crear `idx_producto_nombre_trgm` usando `GIN (LOWER(nombre_producto) gin_trgm_ops)` — optimiza búsquedas `LIKE '%...%'` en nombre
    - Crear `idx_producto_descripcion_trgm` usando `GIN (LOWER(descripcion_producto) gin_trgm_ops)` — optimiza búsquedas `LIKE '%...%'` en descripción
    - Crear `idx_codigo_verificacion_id_usuario` en `usuario_codigo_verificacion(id_usuario)` — optimiza `findByUsuario` y `deleteByIdUsuario`
    - Usar `CREATE INDEX IF NOT EXISTS` en todos los índices para que el script sea idempotente
    - _Requirements: 14.1 – 14.11_

  - [ ] 16.2 Añadir anotaciones `@Table(indexes = {...})` en las entidades JPA afectadas (opcional, para documentar los índices en el modelo)
    - En `CompraEntity`: agregar `@Table(name = "compra", indexes = { @Index(name = "idx_compra_id_usuario", columnList = "id_usuario"), @Index(name = "idx_compra_fecha_compra", columnList = "fecha_compra"), @Index(name = "idx_compra_estado", columnList = "compra_estado") })`
    - En `CompraDetalleEntity`: agregar `@Table(name = "compra_detalle", indexes = { @Index(name = "idx_compra_detalle_id_compra", columnList = "id_compra"), @Index(name = "idx_compra_detalle_id_producto", columnList = "id_producto") })`
    - En `ProductoEntity`: agregar `@Table(name = "producto", indexes = { @Index(name = "idx_producto_categoria", columnList = "id_producto_categoria") })`
    - **Nota**: los índices GIN (trigrama) no se pueden declarar en `@Table(indexes)` porque JPA no soporta `USING GIN`; solo se crean vía el script DDL del paso 16.1
    - _Requirements: 14.1 – 14.8_

---

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido.
- Cada tarea referencia los requisitos específicos para trazabilidad.
- Los checkpoints garantizan validación incremental antes de continuar.
- Los property tests usan jqwik con `@Property(tries = 100)` mínimo.
- Los tests unitarios complementan los property tests con casos concretos y de borde.
- `findByIdWithLock` se declara en `ProductoRepository` (no en `CompraRepository`) porque opera sobre `ProductoEntity`.
- El record `IdempotencyResult<T>` permite que el controlador detecte respuestas repetidas sin una segunda consulta al store.
- La corrección N+1 en `CompraRepository` (paso 9) es independiente del cambio de `FetchType.LAZY` (paso 10); ambos son necesarios.
- El `RateLimitingFilter` se registra antes del `JwtAuthenticationFilter` para rechazar peticiones que excedan el límite antes de validar el JWT.
- Los límites de rate limiting se aplican por combinación de `{IP, endpoint}`, no por IP global.
- La inicialización de buckets es perezosa: el bucket de un cliente se crea en su primera petición usando `computeIfAbsent` para garantizar thread-safety.
- Los índices GIN con `pg_trgm` (tarea 16.1) no son declarables en `@Table(indexes)` de JPA; deben crearse exclusivamente vía script DDL.
- La extensión `pg_trgm` debe habilitarse una sola vez por base de datos con permisos de superusuario o rol `CREATEROLE`.
- La tarea 16.2 es opcional: agregar `@Table(indexes)` en las entidades sirve solo como documentación en el modelo; no genera los índices GIN ni reemplaza el script DDL.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "2.2", "3.1", "3.2"] },
    { "id": 2, "tasks": ["2.3", "3.3", "8.1", "8.2", "8.3", "9.1", "10.1", "10.2"] },
    { "id": 3, "tasks": ["5.1", "5.2", "6.4", "8.4", "9.2"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3", "5.3", "5.4"] },
    { "id": 5, "tasks": ["6.5", "6.6", "12.1", "12.2", "12.3"] },
    { "id": 6, "tasks": ["12.4"] },
    { "id": 7, "tasks": ["14.1", "14.2", "14.3", "14.4", "14.5", "14.6", "15.1", "15.2"] },
    { "id": 8, "tasks": ["15.3"] },
    { "id": 9, "tasks": ["16.1", "16.2"] }
  ]
}
```
