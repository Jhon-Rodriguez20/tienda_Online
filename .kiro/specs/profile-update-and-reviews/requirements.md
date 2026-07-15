# Requirements Document

## Introduction

Este documento define los requisitos para dos módulos nuevos del backend e-commerce Spring Boot: (1) Actualización del perfil de cliente autenticado y (2) Sistema de reseñas de productos. Ambos módulos se integran con la arquitectura existente (JWT, entidades JPA, DTOs, servicios) y siguen los principios de Clean Architecture, SOLID, DRY y KISS.

## Glossary

- **Sistema**: El backend de la tienda online (`com.fesc.tiendaOnline`)
- **Cliente**: Usuario autenticado con rol CLIENTE
- **JWT**: JSON Web Token usado para autenticación stateless
- **Perfil**: Información personal del usuario almacenada en UsuarioEntity
- **Reseña**: Valoración y comentario de un producto por parte de un cliente
- **Compra_Completada**: Compra con estado ACEPTADO o ENTREGADO en CompraEstado
- **UsuarioService**: Servicio existente que gestiona la lógica de usuarios
- **ReviewService**: Nuevo servicio dedicado a la lógica de reseñas
- **ReviewEntity**: Nueva entidad JPA que representa una reseña de producto
- **UsuarioUpdateDTO**: Nuevo DTO para recibir los datos de actualización del perfil
- **UsuarioPerfilResponseDTO**: Nuevo DTO para responder con la información del perfil del usuario autenticado
- **ReviewCreateDTO**: Nuevo DTO para recibir los datos de creación/actualización de reseña
- **ReviewResponseDTO**: Nuevo DTO para responder con la información de una reseña
- **ReviewEstadisticasDTO**: Nuevo DTO para retornar estadísticas agregadas de reseñas
- **ProductoEntity**: Entidad existente que representa un producto
- **CompraDetalleEntity**: Entidad existente que vincula productos con compras
- **CompraEntity**: Entidad existente que representa una compra

---

## Requirements

### Requirement 1: Obtener Perfil del Usuario Autenticado

**User Story:** Como cliente autenticado, quiero consultar mi información personal, para poder ver mis datos actuales antes de editarlos.

#### Acceptance Criteria

1. WHEN el Cliente realiza una petición GET al endpoint de perfil con un JWT válido, THE Sistema SHALL extraer el identificador del usuario desde el claim subject del JWT y retornar un UsuarioPerfilResponseDTO con los campos: nombre, apellido, email, telefono, pais, departamento, ciudad, direccion y codigoPostal
2. THE UsuarioPerfilResponseDTO SHALL incluir el campo email como valor informativo que no es aceptado como parámetro modificable en los endpoints de actualización de perfil
3. IF el token JWT es inválido, ha expirado o se encuentra en la lista negra de tokens revocados, THEN THE Sistema SHALL retornar HTTP 401 con un ErrorResponseDTO que contenga un mensaje indicando que la autenticación ha fallado
4. IF el usuario extraído del JWT no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con un ErrorResponseDTO que contenga un mensaje indicando que el usuario no fue encontrado
5. IF el usuario extraído del JWT tiene estado INACTIVO o CANCELADO, THEN THE Sistema SHALL retornar HTTP 403 con un ErrorResponseDTO que contenga un mensaje indicando que la cuenta no se encuentra activa

---

### Requirement 2: Actualizar Perfil del Usuario Autenticado

**User Story:** Como cliente autenticado, quiero editar mi información personal (nombre, apellido, teléfono, país, departamento, ciudad, dirección, código postal), para mantener mis datos actualizados.

#### Acceptance Criteria

1. WHEN el Cliente envía una petición PUT al endpoint de perfil con un JWT válido y un UsuarioUpdateDTO válido, THE Sistema SHALL actualizar únicamente los campos editables (nombre, apellido, telefono, pais, departamento, ciudad, direccion, codigoPostal) del usuario identificado por el JWT y retornar HTTP 200 con el UsuarioPerfilResponseDTO actualizado
2. THE Sistema SHALL obtener siempre la identidad del usuario desde el JWT y rechazar cualquier intento de enviar un ID de usuario desde el cuerpo de la petición
3. THE UsuarioUpdateDTO SHALL aplicar las siguientes validaciones con Bean Validation: nombre (@NotBlank, @Size min 3 max 100), apellido (@NotBlank, @Size min 3 max 100), telefono (@NotBlank, @Size min 10 max 20, @Pattern regex que acepte únicamente dígitos numéricos), pais (@NotBlank, @Size min 3 max 30), departamento (@NotBlank, @Size min 3 max 50), ciudad (@NotBlank, @Size min 3 max 50), direccion (@NotBlank, @Size min 10 max 100), codigoPostal (opcional, @Size max 17 cuando se proporciona)
4. IF el UsuarioUpdateDTO contiene datos inválidos según las reglas de Bean Validation, THEN THE Sistema SHALL retornar HTTP 400 con un cuerpo que incluya la lista de campos que fallaron la validación junto con el mensaje de error de cada campo
5. IF el número de teléfono proporcionado ya está registrado por otro usuario, THEN THE Sistema SHALL retornar HTTP 409 con el mensaje "El teléfono ya está registrado por otro usuario"
6. THE Sistema SHALL no permitir la modificación de los campos: email, idUsuario, contrasenaEncp, estado y usuarioRol
7. IF el usuario identificado por el JWT no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con un mensaje indicando que el usuario no fue encontrado
8. THE Sistema SHALL agregar el campo "apellido" a la entidad UsuarioEntity como una nueva columna en la base de datos con longitud máxima de 100 caracteres

---

### Requirement 3: Crear o Actualizar una Reseña de Producto

**User Story:** Como cliente autenticado que ha comprado un producto, quiero dejar una reseña con calificación y comentario, para compartir mi experiencia con otros compradores.

#### Acceptance Criteria

1. WHEN el Cliente envía una petición POST al endpoint de reseñas con un JWT válido y un ReviewCreateDTO válido, THE Sistema SHALL verificar que el cliente tiene al menos una compra con estado ACEPTADO o ENTREGADO cuyo detalle contenga el producto referenciado
2. IF el cliente no tiene una compra con estado ACEPTADO o ENTREGADO cuyo detalle incluya el producto, THEN THE Sistema SHALL retornar HTTP 403 con el mensaje "Solo puedes reseñar productos que hayas comprado"
3. IF ya existe una reseña del mismo usuario para el mismo producto, THEN THE Sistema SHALL actualizar los campos estrellas y comentario de la reseña existente con los nuevos valores del ReviewCreateDTO, preservar el campo createdAt original, y asignar la fecha actual al campo updatedAt
4. THE ReviewCreateDTO SHALL aplicar las siguientes validaciones: idProducto (@NotNull, UUID válido), estrellas (@NotNull, @Min 1, @Max 5, tipo Integer), comentario (@NotBlank después de trim, longitud entre 1 y 1000 caracteres después de trim)
5. IF el ReviewCreateDTO contiene datos inválidos, THEN THE Sistema SHALL retornar HTTP 400 con los detalles de validación indicando cada campo que falló y la regla incumplida
6. IF el producto referenciado no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "Producto no encontrado"
7. WHEN la reseña se crea exitosamente, THE Sistema SHALL almacenar el campo comentario con espacios al inicio y final eliminados (trimmed) y asignar la fecha actual a createdAt y updatedAt
8. WHEN la reseña se crea o actualiza exitosamente, THE Sistema SHALL retornar HTTP 200 con el ReviewResponseDTO que incluye: idReview, idProducto, idUsuario, nombreUsuario, estrellas, comentario, createdAt y updatedAt
9. IF el JWT es inválido, está expirado o no se proporciona en la petición, THEN THE Sistema SHALL retornar HTTP 401 sin procesar la operación de reseña

---

### Requirement 4: Eliminar una Reseña Propia

**User Story:** Como cliente autenticado, quiero eliminar mi propia reseña de un producto, para poder retirar mi opinión si lo deseo.

#### Acceptance Criteria

1. WHEN el Cliente envía una petición DELETE al endpoint de reseñas con el identificador de la reseña y un JWT válido, THE Sistema SHALL verificar que la reseña pertenece al usuario autenticado y eliminarla de la base de datos
2. IF la reseña no pertenece al usuario autenticado, THEN THE Sistema SHALL retornar HTTP 403 con el mensaje "No tienes permiso para eliminar esta reseña"
3. IF la reseña no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "Reseña no encontrada"
4. WHEN la eliminación es exitosa, THE Sistema SHALL retornar HTTP 204 sin contenido en el cuerpo de la respuesta
5. IF el JWT es inválido, está expirado o no se incluye en la petición, THEN THE Sistema SHALL retornar HTTP 401 con un mensaje indicando que la autenticación es requerida, sin realizar ninguna modificación sobre la reseña
6. IF el identificador de la reseña no tiene un formato UUID válido, THEN THE Sistema SHALL retornar HTTP 400 con un mensaje indicando que el identificador proporcionado es inválido

---

### Requirement 5: Consultar Reseñas de un Producto (Público)

**User Story:** Como usuario (autenticado o no), quiero ver las reseñas de un producto de forma paginada y ordenada por fecha, para tomar decisiones de compra informadas.

#### Acceptance Criteria

1. WHEN un usuario realiza una petición GET al endpoint de reseñas de un producto con parámetros de paginación (pagina, tamanio), THE Sistema SHALL retornar un PaginacionResponseDTO con las reseñas ordenadas por fecha de creación descendente (más recientes primero), usando valores por defecto pagina=0 y tamanio=10 cuando no se proporcionan los parámetros
2. THE Sistema SHALL permitir el acceso a este endpoint sin autenticación (público)
3. IF el producto no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "Producto no encontrado"
4. THE respuesta paginada SHALL utilizar el PaginacionResponseDTO existente incluyendo: contenido (lista de ReviewResponseDTO), numeroPagina, tamanioPagina, totalElementos, totalPaginas, esUltima y esPrimera
5. IF el parámetro tamanio no es uno de los valores permitidos (10, 25, 50), THEN THE Sistema SHALL utilizar el valor por defecto 10
6. WHEN un usuario solicita una página que excede el total de páginas disponibles, THE Sistema SHALL retornar un PaginacionResponseDTO con la lista de contenido vacía y los metadatos de paginación correspondientes
7. THE ReviewResponseDTO SHALL incluir: idReview, idProducto, idUsuario, nombreUsuario, estrellas, comentario, createdAt y updatedAt

---

### Requirement 6: Consultar Reseña del Usuario Autenticado para un Producto

**User Story:** Como cliente autenticado, quiero ver mi propia reseña para un producto específico, para saber si ya dejé una opinión y cuál fue.

#### Acceptance Criteria

1. WHEN el Cliente envía una petición GET al endpoint para obtener su reseña de un producto específico identificado por su UUID con un JWT válido, THE Sistema SHALL extraer el identificador del usuario desde el JWT, buscar la reseña asociada a ese usuario y producto, y retornar HTTP 200 con el ReviewResponseDTO que incluye: idReview, idProducto, idUsuario, nombreUsuario, estrellas, comentario, createdAt y updatedAt
2. IF el usuario autenticado no tiene una reseña para el producto especificado, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "No has dejado una reseña para este producto"
3. THE Sistema SHALL verificar la existencia del producto antes de buscar la reseña del usuario; IF el producto no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "Producto no encontrado"
4. IF el token JWT es inválido o ha expirado, THEN THE Sistema SHALL retornar HTTP 401 con un mensaje de error descriptivo
5. IF el identificador de producto proporcionado no es un UUID válido, THEN THE Sistema SHALL retornar HTTP 400 con un mensaje de error indicando formato inválido

---

### Requirement 7: Consultar Promedio de Estrellas y Total de Reseñas (Público)

**User Story:** Como usuario (autenticado o no), quiero ver el promedio de calificación y la cantidad total de reseñas de un producto, para tener una referencia rápida de su valoración general.

#### Acceptance Criteria

1. WHEN un usuario realiza una petición GET al endpoint de estadísticas de reseñas de un producto, THE Sistema SHALL retornar HTTP 200 con un ReviewEstadisticasDTO que contenga el promedio de estrellas (redondeado a un decimal usando HALF_UP) y el conteo total de reseñas
2. THE Sistema SHALL permitir el acceso a este endpoint sin autenticación (público)
3. IF el producto no tiene reseñas, THEN THE Sistema SHALL retornar HTTP 200 con un ReviewEstadisticasDTO con promedio 0.0 y conteo 0
4. IF el producto no existe en la base de datos, THEN THE Sistema SHALL retornar HTTP 404 con el mensaje "Producto no encontrado"

---

### Requirement 8: Entidad ReviewEntity y Modelo de Datos

**User Story:** Como desarrollador, quiero una entidad JPA bien diseñada para las reseñas, para garantizar integridad de datos y rendimiento en consultas.

#### Acceptance Criteria

1. THE ReviewEntity SHALL estar mapeada a la tabla "review" y contener los campos: idReview (UUID, clave primaria generada con @UuidGenerator style TIME), producto (ManyToOne a ProductoEntity, FetchType.LAZY), usuario (ManyToOne a UsuarioEntity, FetchType.LAZY), estrellas (Integer, no nulo, columna "estrellas"), comentario (String, máximo 1000 caracteres, no nulo, columna "comentario"), createdAt (LocalDateTime, no nulo, columna "created_at") y updatedAt (LocalDateTime, no nulo, columna "updated_at")
2. THE ReviewEntity SHALL tener una restricción de unicidad compuesta (@Table uniqueConstraints) sobre los campos id_usuario e id_producto para garantizar una sola reseña por usuario por producto a nivel de base de datos
3. THE ReviewEntity SHALL tener índices (@Index) en las columnas id_producto e id_usuario para optimizar consultas de búsqueda por producto y por usuario
4. THE ReviewEntity SHALL seguir las convenciones existentes del proyecto: uso de @UuidGenerator(style = UuidGenerator.Style.TIME), @Getter y @Setter de Lombok, y nombres de columna en snake_case
5. THE campo estrellas SHALL estar restringido a nivel de columna con una check constraint o validación que permita únicamente valores enteros entre 1 y 5 inclusive

---

### Requirement 9: Seguridad y Configuración de Endpoints

**User Story:** Como arquitecto del sistema, quiero que los endpoints estén correctamente protegidos según el tipo de operación, para garantizar que solo usuarios autorizados realicen acciones sobre sus propios recursos.

#### Acceptance Criteria

1. THE SecurityConfig SHALL configurar los endpoints de consulta pública de reseñas (GET reseñas de producto, GET estadísticas) como permitAll sin requerir autenticación, usando .requestMatchers con HttpMethod.GET y las rutas correspondientes
2. THE SecurityConfig SHALL configurar los endpoints de escritura de reseñas (POST crear/actualizar, DELETE eliminar) con .hasRole("CLIENTE"), restringiendo el acceso exclusivamente a usuarios con rol CLIENTE
3. THE SecurityConfig SHALL configurar los endpoints de perfil (GET perfil, PUT actualizar perfil) y el endpoint de consulta de reseña propia (GET reseña del usuario para un producto) como requiriendo autenticación (.authenticated())
4. THE Sistema SHALL documentar todos los endpoints de reseñas y perfil con anotaciones de Swagger/OpenAPI incluyendo descripción del propósito de la operación, todos los códigos de respuesta posibles (200, 204, 400, 401, 403, 404, 409) y esquemas de request/response asociados
5. IF un usuario con rol ADMIN intenta acceder a los endpoints de escritura de reseñas (POST crear/actualizar, DELETE eliminar), THEN THE Sistema SHALL retornar HTTP 403 denegando el acceso
6. IF un usuario no autenticado intenta acceder a un endpoint protegido (escritura de reseñas o perfil), THEN THE Sistema SHALL retornar HTTP 401 indicando que se requiere autenticación

---

### Requirement 10: Arquitectura y Separación de Responsabilidades

**User Story:** Como desarrollador, quiero que el código siga la arquitectura limpia del proyecto, para facilitar el mantenimiento y escalabilidad.

#### Acceptance Criteria

1. THE Sistema SHALL implementar la lógica de actualización de perfil dentro del UsuarioService existente, siguiendo el patrón actual de inyección por constructor y anotación @Service
2. THE Sistema SHALL crear un ReviewService independiente anotado con @Service para toda la lógica de negocio de reseñas, con inyección por constructor de ReviewRepository, ProductoRepository, CompraRepository y CompraDetalleRepository
3. THE Sistema SHALL crear un ReviewController anotado con @RestController y @RequestMapping("/reviews") con sus endpoints REST separado de los controladores existentes
4. THE Sistema SHALL crear un ReviewRepository que extienda JpaRepository<ReviewEntity, UUID> con métodos personalizados para consultas por producto (findByProductoIdProducto), por usuario y producto (findByUsuarioIdUsuarioAndProductoIdProducto), eliminación por id y usuario, y para estadísticas agregadas mediante @Query
5. THE Sistema SHALL usar DTOs separados para cada operación: ReviewCreateDTO (entrada con validaciones), ReviewResponseDTO (salida con todos los campos de la reseña), ReviewEstadisticasDTO (promedioEstrellas como Double y totalResenas como Long)
6. THE Sistema SHALL reutilizar las excepciones existentes (NotFoundException, ForbiddenException, BusinessRuleException) para manejar errores de negocio en las reseñas sin crear nuevas clases de excepción
