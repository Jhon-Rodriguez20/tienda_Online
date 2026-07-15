# Implementation Plan: Profile Update and Reviews

## Overview

This plan implements two modules in the Spring Boot backend: (1) User profile GET/PUT endpoints with a new `apellido` field in `UsuarioEntity`, and (2) A complete product reviews CRUD system with `ReviewEntity`, `ReviewService`, `ReviewController`, and `ReviewRepository`. The implementation follows the project's existing architecture (Controller → Service → Repository), uses Lombok, Bean Validation, JWT security, and jqwik for property-based testing.

## Tasks

- [x] 1. Set up data model layer (Entity + DTOs)
  - [x] 1.1 Add `apellido` field to UsuarioEntity and create ReviewEntity
    - Add `@Column(name = "apellido", length = 100) private String apellido` to `UsuarioEntity`
    - Create `ReviewEntity` in `model/entity/` with fields: idReview (UUID, @UuidGenerator TIME), producto (ManyToOne LAZY to ProductoEntity), usuario (ManyToOne LAZY to UsuarioEntity), estrellas (Integer, not null), comentario (String, max 1000, not null), createdAt (LocalDateTime, not null, updatable=false), updatedAt (LocalDateTime, not null)
    - Add `@Table` annotation with unique constraint `uk_review_usuario_producto` on (id_usuario, id_producto)
    - Add `@Index` annotations for id_producto and id_usuario columns
    - Use @Getter, @Setter from Lombok following project conventions
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 2.8_

  - [x] 1.2 Create profile DTOs (UsuarioUpdateDTO, UsuarioPerfilResponseDTO)
    - Create `UsuarioUpdateDTO` with Bean Validation: nombre (@NotBlank, @Size 3-100), apellido (@NotBlank, @Size 3-100), telefono (@NotBlank, @Size 10-20, @Pattern digits only), pais (@NotBlank, @Size 3-30), departamento (@NotBlank, @Size 3-50), ciudad (@NotBlank, @Size 3-50), direccion (@NotBlank, @Size 10-100), codigoPostal (@Size max 17)
    - Create `UsuarioPerfilResponseDTO` with fields: nombre, apellido, email, telefono, pais, departamento, ciudad, direccion, codigoPostal
    - _Requirements: 1.1, 2.1, 2.3_

  - [x] 1.3 Create review DTOs (ReviewCreateDTO, ReviewResponseDTO, ReviewEstadisticasDTO)
    - Create `ReviewCreateDTO` with Bean Validation: idProducto (@NotNull UUID), estrellas (@NotNull, @Min 1, @Max 5, Integer), comentario (@NotBlank, @Size 1-1000)
    - Create `ReviewResponseDTO` with fields: idReview, idProducto, idUsuario, nombreUsuario, estrellas, comentario, createdAt, updatedAt
    - Create `ReviewEstadisticasDTO` with fields: promedioEstrellas (Double), totalResenas (Long)
    - _Requirements: 3.4, 3.8, 5.7, 7.1, 10.5_

- [x] 2. Implement repository layer
  - [x] 2.1 Create ReviewRepository interface
    - Create `ReviewRepository` extending `JpaRepository<ReviewEntity, UUID>` in `repository/`
    - Add `Page<ReviewEntity> findByProductoIdProducto(UUID idProducto, Pageable pageable)`
    - Add `Optional<ReviewEntity> findByUsuarioIdUsuarioAndProductoIdProducto(UUID idUsuario, UUID idProducto)`
    - Add `@Query` for `promedioEstrellasPorProducto` returning `Optional<Double>`
    - Add `@Query` for `contarPorProducto` returning `Long`
    - _Requirements: 10.4_

- [x] 3. Implement service layer
  - [x] 3.1 Extend UsuarioService with profile methods
    - Add `obtenerPerfil(UUID idUsuario)` method: find user, check state (INACTIVO/CANCELADO → ForbiddenException), map to UsuarioPerfilResponseDTO
    - Add `actualizarPerfil(UUID idUsuario, UsuarioUpdateDTO dto)` method: find user, check state, check phone uniqueness (ConflictException if duplicate), update only editable fields, save, return UsuarioPerfilResponseDTO
    - _Requirements: 1.1, 1.4, 1.5, 2.1, 2.5, 2.6, 2.7, 10.1_

  - [x] 3.2 Create ReviewService with CRUD operations
    - Create `ReviewService` with constructor injection of ReviewRepository, ProductoRepository, CompraRepository, CompraDetalleRepository
    - Implement `crearOActualizar(UUID idUsuario, ReviewCreateDTO dto)`: validate product exists (404), verify qualifying purchase ACEPTADO/ENTREGADO with product in detail (403), upsert logic preserving createdAt on update, trim comentario, return ReviewResponseDTO
    - Implement `eliminar(UUID idReview, UUID idUsuario)`: find review (404), verify ownership (403), delete, return void
    - Implement `obtenerPorProducto(UUID idProducto, int pagina, int tamanio)`: validate product exists (404), normalize tamanio to {10,25,50} defaulting to 10, query paginated sorted by createdAt DESC, map to PaginacionResponseDTO
    - Implement `obtenerEstadisticas(UUID idProducto)`: validate product exists (404), compute average with HALF_UP rounding to 1 decimal, return ReviewEstadisticasDTO (0.0/0 if no reviews)
    - Implement `obtenerReviewUsuario(UUID idUsuario, UUID idProducto)`: validate product exists (404), find review by user+product (404 if not found), return ReviewResponseDTO
    - _Requirements: 3.1, 3.2, 3.3, 3.6, 3.7, 4.1, 4.2, 4.3, 5.1, 5.3, 5.5, 6.1, 6.2, 6.3, 7.1, 7.3, 7.4, 10.2, 10.6_

- [x] 4. Checkpoint - Verify service layer compiles
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement controller layer
  - [x] 5.1 Add profile endpoints to UsuarioController
    - Add `@GetMapping("/perfil")` returning `ResponseEntity<UsuarioPerfilResponseDTO>` – extract user ID from JWT SecurityContext, delegate to UsuarioService.obtenerPerfil
    - Add `@PutMapping("/perfil")` accepting `@Valid @RequestBody UsuarioUpdateDTO` returning `ResponseEntity<UsuarioPerfilResponseDTO>` – extract user ID from JWT, delegate to UsuarioService.actualizarPerfil
    - _Requirements: 1.1, 1.2, 2.1, 2.2_

  - [x] 5.2 Create ReviewController with all endpoints
    - Create `ReviewController` with @RestController @RequestMapping("/reviews")
    - Implement `@PostMapping` crearOActualizarReview: extract user ID from JWT, delegate to ReviewService.crearOActualizar, return 200
    - Implement `@DeleteMapping("/{idReview}")` eliminarReview: extract user ID from JWT, delegate to ReviewService.eliminar, return 204
    - Implement `@GetMapping("/producto/{idProducto}")` obtenerReviewsProducto: delegate with pagina/tamanio params, return 200
    - Implement `@GetMapping("/producto/{idProducto}/estadisticas")` obtenerEstadisticas: delegate, return 200
    - Implement `@GetMapping("/producto/{idProducto}/mi-review")` obtenerMiReview: extract user ID from JWT, delegate, return 200
    - _Requirements: 3.8, 3.9, 4.1, 4.4, 4.5, 5.1, 5.2, 5.4, 6.1, 6.4, 7.1, 7.2, 10.3_

- [x] 6. Configure security for new endpoints
  - [x] 6.1 Update SecurityConfig with review and profile endpoint rules
    - Add `.requestMatchers(HttpMethod.GET, "/reviews/producto/**").permitAll()` for public read access
    - Add `.requestMatchers(HttpMethod.POST, "/reviews").hasRole("CLIENTE")` for review creation
    - Add `.requestMatchers(HttpMethod.DELETE, "/reviews/**").hasRole("CLIENTE")` for review deletion
    - Add `.requestMatchers(HttpMethod.GET, "/reviews/producto/*/mi-review").authenticated()` for own review lookup
    - Add `.requestMatchers(HttpMethod.GET, "/usuario/perfil").authenticated()` and `.requestMatchers(HttpMethod.PUT, "/usuario/perfil").authenticated()` for profile access
    - Ensure order of matchers: specific paths before wildcard paths (mi-review before producto/**)
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 9.6_

- [x] 7. Checkpoint - Verify full compilation and basic wiring
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Property-based tests with jqwik
  - [ ]* 8.1 Write property test for profile mapping (Property 1)
    - **Property 1: Profile mapping preserves all fields**
    - Create jqwik test class in test package with `@Property(tries = 100)`
    - Generate arbitrary UsuarioEntity instances with valid random data
    - Assert that mapping to UsuarioPerfilResponseDTO preserves all field values (nombre, apellido, email, telefono, pais, departamento, ciudad, direccion, codigoPostal)
    - Tag: `Feature: profile-update-and-reviews, Property 1: Profile mapping preserves all fields`
    - **Validates: Requirements 1.1**

  - [ ]* 8.2 Write property test for profile update immutability (Property 2)
    - **Property 2: Profile update only modifies editable fields**
    - Generate arbitrary UsuarioEntity and UsuarioUpdateDTO
    - Apply update and assert protected fields (idUsuario, email, contrasenaEncp, estado, usuarioRol) remain unchanged
    - Assert editable fields match DTO values
    - Tag: `Feature: profile-update-and-reviews, Property 2: Profile update only modifies editable fields`
    - **Validates: Requirements 2.1, 2.6**

  - [ ]* 8.3 Write property test for UsuarioUpdateDTO validation (Property 3)
    - **Property 3: UsuarioUpdateDTO validation rejects invalid inputs**
    - Generate arbitrary strings violating each constraint (too short/long nombres, non-digit telefono, etc.)
    - Assert Bean Validation produces at least one violation for each invalid field
    - Tag: `Feature: profile-update-and-reviews, Property 3: UsuarioUpdateDTO validation rejects invalid inputs`
    - **Validates: Requirements 2.3**

  - [ ]* 8.4 Write property test for review purchase requirement (Property 4)
    - **Property 4: Review creation requires qualifying purchase**
    - Generate user-product combinations with and without qualifying purchases (ACEPTADO/ENTREGADO)
    - Assert creation succeeds only when qualifying purchase exists
    - Tag: `Feature: profile-update-and-reviews, Property 4: Review creation requires qualifying purchase`
    - **Validates: Requirements 3.1, 3.2**

  - [ ]* 8.5 Write property test for review upsert preserving createdAt (Property 5)
    - **Property 5: Review upsert preserves createdAt on update**
    - Generate existing review and new ReviewCreateDTO for same user-product
    - Assert createdAt is preserved, updatedAt is refreshed, estrellas/comentario updated
    - Tag: `Feature: profile-update-and-reviews, Property 5: Review upsert preserves createdAt on update`
    - **Validates: Requirements 3.3**

  - [ ]* 8.6 Write property test for review comment trimming (Property 6)
    - **Property 6: Review comment is stored trimmed**
    - Generate arbitrary strings with leading/trailing whitespace
    - Assert stored comentario equals input.trim() and response DTO reflects trimmed value
    - Tag: `Feature: profile-update-and-reviews, Property 6: Review comment is stored trimmed`
    - **Validates: Requirements 3.7**

  - [ ]* 8.7 Write property test for ReviewCreateDTO validation (Property 7)
    - **Property 7: ReviewCreateDTO validation rejects invalid inputs**
    - Generate DTOs with null/out-of-range estrellas, null idProducto, blank/over-1000 comentario
    - Assert Bean Validation produces at least one violation
    - Tag: `Feature: profile-update-and-reviews, Property 7: ReviewCreateDTO validation rejects invalid inputs`
    - **Validates: Requirements 3.4, 8.5**

  - [ ]* 8.8 Write property test for review ownership enforcement (Property 8)
    - **Property 8: Review ownership enforcement on delete**
    - Generate review with owner UUID and a different authenticated UUID
    - Assert deletion fails with ForbiddenException when UUIDs differ, succeeds when equal
    - Tag: `Feature: profile-update-and-reviews, Property 8: Review ownership enforcement on delete`
    - **Validates: Requirements 4.1, 4.2**

  - [ ]* 8.9 Write property test for pagination ordering (Property 9)
    - **Property 9: Reviews pagination is ordered by createdAt descending**
    - Generate list of reviews with random createdAt values
    - Assert paginated response maintains descending order (each createdAt >= next)
    - Tag: `Feature: profile-update-and-reviews, Property 9: Reviews pagination is ordered by createdAt descending`
    - **Validates: Requirements 5.1**

  - [ ]* 8.10 Write property test for page size defaulting (Property 10)
    - **Property 10: Page size defaults to 10 for invalid values**
    - Generate arbitrary integers not in {10, 25, 50}
    - Assert system uses page size 10; for valid values assert system uses provided value
    - Tag: `Feature: profile-update-and-reviews, Property 10: Page size defaults to 10 for invalid values`
    - **Validates: Requirements 5.5**

  - [ ]* 8.11 Write property test for statistics average rounding (Property 11)
    - **Property 11: Statistics average uses HALF_UP rounding to one decimal**
    - Generate lists of integers 1-5 (star ratings)
    - Compute expected average with BigDecimal HALF_UP scale 1
    - Assert promedioEstrellas matches expected; totalResenas matches count
    - Tag: `Feature: profile-update-and-reviews, Property 11: Statistics average uses HALF_UP rounding to one decimal`
    - **Validates: Requirements 7.1**

  - [ ]* 8.12 Write property test for ReviewEntity to DTO mapping (Property 12)
    - **Property 12: ReviewEntity to ReviewResponseDTO mapping completeness**
    - Generate arbitrary ReviewEntity with associated UsuarioEntity and ProductoEntity
    - Assert all fields map correctly: idReview, idProducto, idUsuario, nombreUsuario (from usuario.nombre), estrellas, comentario, createdAt, updatedAt
    - Tag: `Feature: profile-update-and-reviews, Property 12: ReviewEntity to ReviewResponseDTO mapping completeness`
    - **Validates: Requirements 3.8, 5.7**

- [x] 9. Unit and integration tests
  - [x] 9.1 Write unit tests for UsuarioService profile methods
    - Test obtenerPerfil with inactive user → ForbiddenException
    - Test obtenerPerfil with non-existent user → NotFoundException
    - Test actualizarPerfil with duplicate phone → ConflictException
    - Test actualizarPerfil success → fields updated correctly
    - _Requirements: 1.4, 1.5, 2.5, 2.7_

  - [x] 9.2 Write unit tests for ReviewService methods
    - Test crearOActualizar with non-existent product → NotFoundException
    - Test crearOActualizar without qualifying purchase → ForbiddenException
    - Test eliminar with non-existent review → NotFoundException
    - Test eliminar with non-owner → ForbiddenException
    - Test eliminar success → returns 204
    - Test obtenerEstadisticas with no reviews → 0.0 / 0
    - Test obtenerReviewUsuario with no review → NotFoundException
    - Test obtenerPorProducto with out-of-range page → empty content
    - _Requirements: 3.2, 3.6, 4.2, 4.3, 5.6, 6.2, 7.3_

  - [x] 9.3 Write integration tests for security configuration
    - Test public GET endpoints accessible without token (reviews list, statistics)
    - Test protected endpoints reject requests without token → 401
    - Test ADMIN role rejected on POST/DELETE review endpoints → 403
    - Test CLIENTE role can POST/DELETE reviews → 200/204
    - Test profile endpoints require authentication → 401 without token
    - Test Bean Validation returns structured 400 errors
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 9.6_

- [ ] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik 1.8.4 (already in pom.xml)
- Unit tests validate specific examples and edge cases with JUnit 5 + Mockito
- Integration tests validate security rules and end-to-end flows with Spring Boot Test
- The project uses Java 25, Spring Boot 4.0.5, PostgreSQL, Lombok, and JWT (jjwt 0.12.6)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["3.1", "3.2"] },
    { "id": 3, "tasks": ["5.1", "5.2"] },
    { "id": 4, "tasks": ["6.1"] },
    { "id": 5, "tasks": ["8.1", "8.2", "8.3", "8.7", "8.12"] },
    { "id": 6, "tasks": ["8.4", "8.5", "8.6", "8.8", "8.9", "8.10", "8.11"] },
    { "id": 7, "tasks": ["9.1", "9.2"] },
    { "id": 8, "tasks": ["9.3"] }
  ]
}
```
