# Implementation Plan: transacciones-cache-optimizacion

## Overview

Implementación de cuatro mejoras ortogonales sobre la tienda online Spring Boot 4 / Java 25 / PostgreSQL:
1. **Idempotencia** en `POST /compras/realizar` y `PUT /compras/admin/{compraId}/estado` mediante el header `Idempotency-Key`.
2. **Refuerzo ACID** en `CompraService` y `ProductoService` (aislamiento `REPEATABLE_READ`, bloqueo pesimista, `saveAll` post-loop, `@Transactional(readOnly=true)`).
3. **Caché en memoria con Caffeine** para las regiones `productos`, `busquedaProductos` y `productoPorId`.
4. **Corrección N+1** en entidades JPA, repositorios y servicio.

Las cuatro mejoras son independientes y no requieren cambios de esquema de base de datos.

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

- [ ] 7. Modificar `ProductoRepository` — corrección N+1 y bloqueo pesimista
  - [ ] 7.1 Agregar `countQuery` a todas las queries paginadas en `ProductoRepository`
    - Agregar `countQuery = "SELECT COUNT(p) FROM ProductoEntity p"` a `findAllWithDetails`
    - Agregar `countQuery` sin `JOIN FETCH` a `buscarPorNombre`, `buscarPorTermino`, `buscarPorNombreOrdenado` y `buscarPorCategoriaYNombre`
    - _Requirements: 11.1, 11.2_

  - [ ] 7.2 Agregar método `buscarPorCategoria` en `ProductoRepository`
    - Declarar `Page<ProductoEntity> buscarPorCategoria(@Param("categoriaId") UUID categoriaId, Pageable pageable)`
    - Usar `@Query(value = "SELECT p FROM ProductoEntity p JOIN FETCH p.categoria JOIN FETCH p.usuario WHERE p.categoria.idCategoria = :categoriaId", countQuery = "SELECT COUNT(p) FROM ProductoEntity p WHERE p.categoria.idCategoria = :categoriaId")`
    - _Requirements: 11.3, 11.4_

  - [ ] 7.3 Agregar método `findByIdWithLock` en `ProductoRepository`
    - Declarar `Optional<ProductoEntity> findByIdWithLock(@Param("id") UUID id)`
    - Anotar con `@Lock(LockModeType.PESSIMISTIC_WRITE)` y `@Query("SELECT p FROM ProductoEntity p WHERE p.idProducto = :id")`
    - _Requirements: 5.1, 5.2, 5.3_

- [ ] 8. Modificar `CompraRepository` — corrección N+1
  - [ ] 8.1 Agregar `countQuery` a las queries paginadas en `CompraRepository`
    - Agregar `countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c WHERE c.usuario.idUsuario = :usuarioId"` a `findByUsuarioId`
    - Agregar `countQuery = "SELECT COUNT(DISTINCT c) FROM CompraEntity c"` a `findAllWithDetails`
    - Agregar `countQuery` apropiado a `findByFechaBetween` y `findByUsuarioIdAndFechaBetween`
    - _Requirements: 10.5_

- [ ] 9. Modificar entidades JPA — corrección N+1
  - [ ] 9.1 Cambiar `FetchType.EAGER` a `FetchType.LAZY` en `CompraEntity`
    - En `CompraEntity`, cambiar la relación `@OneToMany detalles` de `fetch = FetchType.EAGER` a `fetch = FetchType.LAZY`
    - _Requirements: 10.1_

  - [ ] 9.2 Cambiar `FetchType.EAGER` a `FetchType.LAZY` en `CompraDetalleEntity`
    - En `CompraDetalleEntity`, cambiar `@ManyToOne compra` de `FetchType.EAGER` a `FetchType.LAZY`
    - En `CompraDetalleEntity`, cambiar `@ManyToOne producto` de `FetchType.EAGER` a `FetchType.LAZY`
    - _Requirements: 10.2, 10.3_

- [ ] 10. Checkpoint — Verificar corrección N+1
  - Asegurarse de que los tests de los pasos 8, 9 y 10 pasan y que el proyecto compila. Verificar que `findByIdWithDetails` en `CompraRepository` ya incluye los `LEFT JOIN FETCH` necesarios para cargar detalles, producto, método de pago y usuario. Consultar al usuario si surgen dudas.

- [ ] 11. Modificar `ProductoService` — caché y corrección N+1
  - [ ] 11.1 Agregar `@Cacheable` y `@Transactional(readOnly=true)` a los métodos de consulta
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "productos", key = "#pagina + '-' + #tamanio")` a `getProductos`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#termino + '-' + #pagina + '-' + #tamanio")` a `buscarProductosPorTermino`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#nombre + '-' + #pagina + '-' + #tamanio")` a `buscarProductosPorNombre`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "busquedaProductos", key = "#productoBusquedaDTO.termino + '-' + #productoBusquedaDTO.categoriaId + '-' + #productoBusquedaDTO.pagina + '-' + #productoBusquedaDTO.tamanio")` a `buscarProductosAvanzado`
    - Agregar `@Transactional(readOnly = true)` y `@Cacheable(value = "productoPorId", key = "#idProducto")` a `getProductoById`
    - _Requirements: 6.2, 7.2, 7.3, 8.1, 8.2, 8.3, 9.1, 9.2_

  - [ ] 11.2 Reemplazar filtro en memoria en `buscarProductosAvanzado` por consulta a BD
    - En la rama `else if (categoriaIdOpt.isPresent())` de `buscarProductosAvanzado`, reemplazar `productoRepository.findAllWithDetails(pageable)` + `stream().filter()` por `productoRepository.buscarPorCategoria(categoriaIdOpt.get(), pageable)`
    - Eliminar la construcción del `PageImpl` manual con la lista filtrada
    - _Requirements: 11.3, 11.4_

  - [ ] 11.3 Agregar `@CacheEvict` a los métodos de modificación en `ProductoService`
    - En `crearProducto`: agregar `@CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true)`
    - En `actualizarProducto`: agregar `@Caching(evict = { @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true), @CacheEvict(value = "productoPorId", key = "#idProducto") })`
    - En `eliminarProducto`: agregar `@Caching(evict = { @CacheEvict(value = {"productos", "busquedaProductos"}, allEntries = true), @CacheEvict(value = "productoPorId", key = "#idProducto") })`
    - _Requirements: 7.5, 8.5, 9.4, 9.5_

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
    { "id": 6, "tasks": ["12.4"] }
  ]
}
```
