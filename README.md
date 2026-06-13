# Tienda Online — API REST

API REST para una tienda en línea construida con Spring Boot. Gestiona usuarios, productos, compras y pagos integrados con la pasarela **Wompi**. Implementa autenticación JWT con RSA, control de acceso por roles, rate limiting, caché en memoria e idempotencia en operaciones críticas.

---

## Tabla de contenido

1. [Descripción del proyecto](#1-descripción-del-proyecto)
2. [Stack tecnológico y justificación](#2-stack-tecnológico-y-justificación)
3. [Generación de llaves RSA](#3-generación-de-llaves-rsa)
4. [Seguridad](#4-seguridad)
5. [Arquitectura](#5-arquitectura)
6. [Configuración de Wompi](#6-configuración-de-wompi)
7. [Configuración del entorno](#7-configuración-del-entorno)
8. [Documentación de endpoints](#8-documentación-de-endpoints)

---

## 1. Descripción del proyecto

Tienda Online es una API REST stateless que expone los recursos necesarios para operar un comercio electrónico:

- **Registro y autenticación** de usuarios con verificación por correo electrónico y recuperación de contraseña.
- **Catálogo de productos** con paginación, búsqueda por término/nombre y búsqueda avanzada filtrada.
- **Proceso de compra** con integración a Wompi para pagos en línea (tarjeta de crédito, Nequi y Bancolombia Transfer).
- **Gestión administrativa** de productos y estados de compra.
- **Webhook de Wompi** para actualización automática del estado de pago.

La aplicación está diseñada para ejecutarse en producción sobre HTTPS y soporta múltiples perfiles de entorno.

---

## 2. Stack tecnológico y justificación

| Tecnología | Versión | Justificación |
|---|---|---|
| **Java** | 25 | Última versión LTS con mejoras de rendimiento y nuevas características del lenguaje. |
| **Spring Boot** | 4.0.5 | Framework estándar para APIs REST en Java. Autoconfiguración, ecosistema maduro y amplio soporte de seguridad. |
| **Spring Security** | (managed) | Manejo declarativo de autenticación y autorización, integración nativa con JWT y BCrypt. |
| **Spring Data JPA** | (managed) | Abstracción sobre Hibernate que reduce el código de acceso a datos y facilita las migraciones. |
| **Spring Validation** | (managed) | Validación de DTOs mediante anotaciones estándar (JSR-380) sin lógica adicional en los controladores. |
| **Spring Mail** | (managed) | Envío de correos transaccionales (verificación y recuperación de contraseña) sobre SMTP con STARTTLS. |
| **Spring Actuator** | (managed) | Métricas y health checks sin configuración extra, útil para monitoreo en producción. |
| **PostgreSQL** | (managed) | Base de datos relacional robusta, con soporte nativo para UUID como tipo de dato primario. |
| **JJWT** | 0.12.6 | Librería de referencia para generación y validación de JWT en Java. Soporta RS256 (RSA) directamente. |
| **Lombok** | (managed) | Elimina código boilerplate (getters, setters, constructores) manteniendo los modelos limpios. |
| **Caffeine Cache** | 3.1.8 | Implementación de caché en memoria de alto rendimiento, integrada con Spring Cache. Evita consultas repetidas a la BD para productos. |
| **Bucket4j** | 8.10.1 | Rate limiting basado en el algoritmo token bucket, sin dependencias externas. Protege los endpoints contra abuso. |
| **jqwik** | 1.8.4 | Testing basado en propiedades (property-based testing) como complemento a los tests unitarios tradicionales. |

---

## 3. Generación de llaves RSA

La aplicación usa **JWT firmado con RS256** (RSA 4096 bits). Se requieren dos archivos PEM: la clave privada para firmar tokens y la clave pública para verificarlos.

### Prerrequisitos

Tener instalado [OpenSSL](https://www.openssl.org/). En Windows puedes usar Git Bash, WSL o la distribución oficial.

### Pasos

**1. Generar la clave privada RSA de 4096 bits:**

```bash
openssl genrsa -out private_key.pem 4096
```

**2. Extraer la clave pública a partir de la privada:**

```bash
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

**3. Verificar los archivos generados:**

```bash
openssl rsa -in private_key.pem -check
openssl rsa -in public_key.pem -pubin -text -noout
```

Los archivos `private_key.pem` y `public_key.pem` deben ubicarse en la raíz del proyecto (o en la ruta que configures en las variables de entorno).

### Variables de entorno relacionadas

```env
JWT_PRIVATE_KEY_LOCATION=file:private_key.pem
JWT_PUBLIC_KEY_LOCATION=file:public_key.pem
```

> **Importante:** nunca subas `private_key.pem` al repositorio. Ya está incluido en `.gitignore`.

---

## 4. Seguridad

La seguridad de la aplicación es multidimensional. A continuación se describe cada capa.

### 4.1 Autenticación — JWT con RSA (RS256)

El acceso a recursos protegidos requiere un **Access Token JWT** en la cabecera `Authorization: Bearer <token>`.

- El token se firma con RSA 4096 bits (algoritmo RS256), por lo que la firma solo puede generarse con la clave privada, pero cualquiera con la clave pública puede verificarla.
- El token incluye un campo `jti` (JWT ID) único por emisión.
- La expiración por defecto es de **15 minutos** (`JWT_EXPIRATION_MS=900000`).
- Al expirar el access token, el cliente puede obtener uno nuevo enviando el `refreshToken` al endpoint `/auth/refresh`.

**Flujo de autenticación:**

```
Cliente → POST /auth/login → { accessToken, refreshToken, expiraEn }
Cliente → requests con Header: Authorization: Bearer <accessToken>
Cliente → POST /auth/refresh → nuevo accessToken
Cliente → POST /auth/logout → token invalidado (blacklist)
```

### 4.2 Blacklist de tokens (Logout)

Al hacer logout (`POST /auth/logout`), el `jti` del token se agrega a una **blacklist en memoria**. El filtro `JwtAuthenticationFilter` consulta esta blacklist en cada request; si el token está revocado devuelve `401`.

### 4.3 Control de acceso por roles (RBAC)

Existen dos roles:

| Rol | Descripción |
|---|---|
| `CLIENTE` | Usuario registrado que puede comprar, ver sus compras y gestionar su cuenta. |
| `ADMIN` | Administrador con acceso total: gestión de productos, consulta de todas las compras y actualización de estados. |

La autorización se aplica en `SecurityConfig` usando `hasRole("ADMIN")` y `hasRole("CLIENTE")`.

### 4.4 Rate Limiting (Bucket4j)

Cada IP tiene un cupo de requests por minuto, separado por tipo de endpoint:

| Endpoint | Límite |
|---|---|
| `/auth/*` | 10 req/min |
| `/productos/*` | 100 req/min |
| `/compras/*` | 30 req/min |
| `/usuarios/*` | 20 req/min |
| Resto | 100 req/min |

Cuando se supera el límite, la respuesta es `429 Too Many Requests` con el header `Retry-After` indicando los segundos de espera. Las cabeceras `X-RateLimit-Remaining` y `X-RateLimit-Limit` están disponibles en cada respuesta.

### 4.5 Idempotencia

Las operaciones que generan side effects (crear una compra, actualizar el estado de una compra) requieren el header `Idempotency-Key: <UUID v4>`. Si se repite la misma clave dentro del TTL de 24 horas, se devuelve la respuesta original sin ejecutar la operación nuevamente. La respuesta duplicada incluye el header `Idempotency-Replayed: true`.

### 4.6 Seguridad HTTP (Headers)

- `X-Frame-Options: DENY` — previene clickjacking.
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` — fuerza HTTPS.
- En perfil `prod`, el servidor redirige automáticamente HTTP → HTTPS y el CORS solo acepta orígenes con `https://`.

### 4.7 CORS

Los orígenes permitidos se configuran con la variable `CORS_ALLOWED_ORIGINS`. Solo se aceptan los métodos `GET, POST, PUT, DELETE, OPTIONS` y un conjunto controlado de headers.

### 4.8 Contraseñas

Las contraseñas se almacenan hasheadas con **BCrypt**. Nunca se almacena ni transmite la contraseña en texto plano.

### 4.9 Verificación de cuenta por correo

Al registrarse, el usuario recibe un código numérico de 6 dígitos al correo. La cuenta permanece inactiva hasta que se verifique mediante `POST /usuario/verificar`.

### 4.10 Validación de firma en webhook Wompi

El endpoint `/pagos/wompi/webhook` es público, pero el servicio `WompiWebhookService` verifica la firma del evento usando la clave `wompi.events-key` antes de procesar el payload.

### 4.11 Manejo global de excepciones

`GlobalExceptionHandler` captura todas las excepciones y devuelve respuestas estructuradas (`ErrorResponseDTO`) con código de error semántico, mensaje y path. Nunca se exponen stack traces al cliente.

---

## 5. Arquitectura

La aplicación sigue una **arquitectura monolítica por capas** (Layered Architecture), organizada para facilitar la migración futura a microservicios.

```
tiendaOnline/
├── config/          → Configuración transversal
│   ├── SecurityConfig.java          # Spring Security, CORS, HTTPS
│   ├── JwtAuthenticationFilter.java # Filtro JWT por request
│   ├── RateLimitingFilter.java      # Rate limiting con Bucket4j
│   ├── CacheConfig.java             # Regiones Caffeine
│   ├── WompiConfig.java             # Credenciales Wompi
│   ├── AdminInitializer.java        # Creación del admin inicial
│   ├── CategoriaDataLoader.java     # Carga de categorías iniciales
│   ├── MetodoPagoDataLoader.java    # Métodos de pago iniciales
│   └── UsuarioRolDataLoader.java    # Roles iniciales
│
├── controller/      → Capa de presentación (HTTP)
│   ├── AuthController.java          # Login, refresh, logout
│   ├── UsuarioController.java       # Registro, verificación, recuperación
│   ├── ProductoController.java      # CRUD productos, búsqueda, categorías
│   ├── CompraController.java        # Compras (cliente y admin)
│   └── WompiWebhookController.java  # Webhook de pagos
│
├── service/         → Lógica de negocio
│   ├── AuthService
│   ├── JwtService / JwtBlacklist
│   ├── UsuarioService
│   ├── ProductoService
│   ├── CompraService
│   ├── WompiService
│   └── WompiWebhookService
│
├── model/dto/       → Objetos de transferencia de datos
│   └── (29 DTOs — request, response, búsqueda, paginación, Wompi, error)
│
├── exception/       → Jerarquía de excepciones y handler global
│   ├── ApiException (base)
│   ├── ConflictException / ForbiddenException / NotFoundException
│   ├── UnauthorizedException / WompiTimeoutException
│   └── GlobalExceptionHandler
│
└── security/
    └── UserDetailsImpl  # Adaptador Spring Security ↔ modelo de usuario
```

### Flujo de una request autenticada

```
Request HTTP
    ↓
RateLimitingFilter      → 429 si se supera el límite
    ↓
JwtAuthenticationFilter → 401 si token inválido o en blacklist
    ↓
SecurityConfig          → 403 si rol insuficiente
    ↓
Controller              → valida DTO (@Valid)
    ↓
Service                 → lógica de negocio, acceso a BD y servicios externos
    ↓
Response HTTP
```

### Consideraciones de microservicios

Aunque la aplicación es monolítica, cada bounded context está aislado:

- **Auth service** → `AuthController` + `JwtService`
- **User service** → `UsuarioController` + `UsuarioService`
- **Product service** → `ProductoController` + `ProductoService`
- **Order service** → `CompraController` + `CompraService`
- **Payment service** → `WompiService` + `WompiWebhookService`

Esta separación permite extraer cada contexto como microservicio independiente con cambios mínimos.

---

## 6. Configuración de Wompi

[Wompi](https://docs.wompi.co) es la pasarela de pagos utilizada para procesar transacciones en Colombia. Soporta tarjeta de crédito, Nequi y Bancolombia Transfer.

### 6.1 Obtener credenciales

1. Crear una cuenta en [sandbox.wompi.co](https://sandbox.wompi.co) para desarrollo o en [comercios.wompi.co](https://comercios.wompi.co) para producción.
2. En el panel de comercio ir a **Desarrolladores → Llaves de API**.
3. Copiar las cuatro claves:
   - `pub_stagtest_...` → Llave pública (Public Key)
   - `prv_stagtest_...` → Llave privada (Private Key)
   - `stagtest_events_...` → Llave de eventos (para verificar webhooks)
   - `stagtest_integrity_...` → Llave de integridad (para firmar transacciones)

### 6.2 Variables de entorno

```env
WOMPI_PUBLIC_KEY=pub_stagtest_xxxxxxxxxxxx
WOMPI_PRIVATE_KEY=prv_stagtest_xxxxxxxxxxxx
WOMPI_EVENTS_KEY=stagtest_events_xxxxxxxxxxxx
WOMPI_INTEGRITY_KEY=stagtest_integrity_xxxxxxxxxxxx
WOMPI_BASE_URL=https://sandbox.wompi.co/v1
```

Para producción cambiar `WOMPI_BASE_URL=https://api.wompi.co/v1` y usar las claves de producción.

### 6.3 Configuración del webhook

Wompi envía notificaciones a tu servidor cuando cambia el estado de una transacción. Para recibirlas:

1. En el panel de Wompi ir a **Desarrolladores → Eventos**.
2. Registrar la URL: `https://tu-dominio.com/pagos/wompi/webhook`
3. El endpoint ya está configurado en la aplicación y verifica la firma `x-event-checksum` usando la `WOMPI_EVENTS_KEY`.

### 6.4 Métodos de pago soportados

| Tipo (`wompiTipoPago`) | Campos adicionales en `CompraRequestDTO` |
|---|---|
| `CARD` | `wompiCardToken` (token de tarjeta tokenizada), `cuotas` (1–36) |
| `NEQUI` | `wompiNequiPhone` (número celular de 10 dígitos registrado en Nequi) |
| `BANCOLOMBIA_TRANSFER` | No requiere campos adicionales |

### 6.5 Flujo de pago

```
1. Cliente consulta acceptance_token → GET /compras/metodo/pago
2. Cliente envía CompraRequestDTO → POST /compras/realizar
3. Aplicación crea transacción en Wompi
4. Para BANCOLOMBIA_TRANSFER y NEQUI, la respuesta incluye asyncPaymentUrl para redirigir al usuario
5. Wompi notifica el resultado → POST /pagos/wompi/webhook
6. La aplicación actualiza el estado de la compra
7. Cliente consulta estado → GET /compras/{compraId}/pago/estado
```

---

## 7. Configuración del entorno

### 7.1 Prerrequisitos

- Java 25
- Maven 3.9+
- PostgreSQL 14+
- OpenSSL (para generar las llaves RSA)

### 7.2 Variables de entorno

Copiar `.env.example` a `.env` y completar los valores:

```env
# Base de datos
JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/tiendaOnline
JDBC_DATABASE_USER=postgres
JDBC_DATABASE_PASSWORD=tu-contraseña

# Correo (App Password de Gmail: Cuenta → Seguridad → Verificación en 2 pasos → Contraseñas de aplicación)
MAIL_USERNAME=tu-correo@gmail.com
MAIL_PASSWORD=tu-contraseña-de-aplicacion

# JWT
JWT_PRIVATE_KEY_LOCATION=file:private_key.pem
JWT_PUBLIC_KEY_LOCATION=file:public_key.pem

# Hibernate
JPA_DDL_AUTO=update
JPA_SHOW_SQL=false

# Servidor
PORT=8080

# CORS (en producción usar HTTPS)
CORS_ALLOWED_ORIGINS=http://localhost:3000

# Wompi
WOMPI_PUBLIC_KEY=pub_stagtest_...
WOMPI_PRIVATE_KEY=prv_stagtest_...
WOMPI_EVENTS_KEY=stagtest_events_...
WOMPI_INTEGRITY_KEY=stagtest_integrity_...
WOMPI_BASE_URL=https://sandbox.wompi.co/v1
```

### 7.3 Ejecutar la aplicación

```bash
# Compilar
./mvnw clean package -DskipTests

# Ejecutar
./mvnw spring-boot:run
```

La aplicación estará disponible en `http://localhost:8080`.

---

## 8. Documentación de endpoints

Base URL: `http://localhost:8080`

Todos los endpoints protegidos requieren el header:
```
Authorization: Bearer <accessToken>
```

---

### Auth — `/auth`

#### POST `/auth/login`
Inicia sesión y obtiene los tokens.

**Request:**
```json
{
  "email": "usuario@ejemplo.com",
  "contrasena": "miPassword123"
}
```

**Response `200 OK`:**
```json
{
  "idUsuario": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "nombre": "Juan Pérez",
  "email": "usuario@ejemplo.com",
  "rol": "CLIENTE",
  "urlImagen": "/images/sinImagenPerfil.webp",
  "expiraEn": 900000,
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

---

#### POST `/auth/refresh`
Renueva el access token usando el refresh token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response `200 OK`:** mismo esquema que `/auth/login`.

---

#### POST `/auth/logout` 🔒
Invalida el access token actual.

**Headers:**
```
Authorization: Bearer <accessToken>
```

**Response `204 No Content`**

---

### Usuarios — `/usuario`

#### POST `/usuario/registro`
Registra un nuevo usuario. Envía un código de verificación al correo.

**Request:**
```json
{
  "nombre": "Juan Pérez",
  "email": "juan@ejemplo.com",
  "contrasena": "miPassword123",
  "telefono": "3001234567",
  "pais": "Colombia",
  "ciudad": "Bogotá",
  "direccion": "Cra 7 # 32-16 Apto 501",
  "departamento": "Cundinamarca",
  "codigoPostal": "110111"
}
```

**Response `201 Created`:**
```json
{
  "idUsuario": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "nombre": "Juan Pérez",
  "email": "juan@ejemplo.com",
  "telefono": "3001234567",
  "pais": "Colombia",
  "ciudad": "Bogotá",
  "direccion": "Cra 7 # 32-16 Apto 501",
  "departamento": "Cundinamarca",
  "codigoPostal": "110111",
  "estado": "PENDIENTE",
  "rol": "CLIENTE",
  "urlImagen": "/images/sinImagenPerfil.webp"
}
```

---

#### POST `/usuario/verificar`
Verifica la cuenta con el código recibido por correo.

**Request:**
```json
{
  "email": "juan@ejemplo.com",
  "codigoVerificacion": "482910"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Código verificado correctamente.",
  "status": "success"
}
```

---

#### POST `/usuario/reenviar-codigo`
Reenvía el código de verificación al correo.

**Request:**
```json
{
  "email": "juan@ejemplo.com"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Código de verificación reenviado exitosamente.",
  "status": "success"
}
```

---

#### POST `/usuario/recuperar/solicitar`
Solicita el código de recuperación de contraseña.

**Request:**
```json
{
  "email": "juan@ejemplo.com"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Código de recuperación enviado exitosamente.",
  "status": "success"
}
```

---

#### POST `/usuario/recuperar/verificar`
Verifica el código de recuperación de contraseña.

**Request:**
```json
{
  "email": "juan@ejemplo.com",
  "codigoVerificacion": "738291"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Código de recuperación verificado correctamente.",
  "status": "success"
}
```

---

#### POST `/usuario/recuperar/cambiar-contrasena`
Establece la nueva contraseña tras verificar el código.

**Request:**
```json
{
  "email": "juan@ejemplo.com",
  "nuevaContrasena": "nuevaPassword456",
  "confirmarContrasena": "nuevaPassword456"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Contraseña cambiada correctamente.",
  "status": "success"
}
```

---

#### DELETE `/usuario/cancelar-cuenta` 🔒
Cancela la cuenta del usuario autenticado.

**Request:**
```json
{
  "contrasena": "miPassword123"
}
```

**Response `200 OK`:**
```json
{
  "mensaje": "Tu cuenta ha sido cancelada exitosamente.",
  "status": "success"
}
```

---

### Productos — `/productos`

#### GET `/productos`
Lista productos paginados. Público.

**Query params:** `pagina=0` `tamanio=10` (valores permitidos: 10, 25, 50)

**Response `200 OK`:**
```json
{
  "contenido": [
    {
      "idProducto": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
      "nombreProducto": "Camiseta Básica",
      "descripcionProducto": "Camiseta de algodón 100% en varios colores",
      "precioProducto": 49900.0,
      "stockProducto": 150,
      "urlImagenProducto": "/uploads/camiseta-basica.webp",
      "nombreCategoria": "Ropa",
      "nombreUsuario": "Admin"
    }
  ],
  "numeroPagina": 0,
  "tamanioPagina": 10,
  "totalElementos": 45,
  "totalPaginas": 5,
  "esPrimera": true,
  "esUltima": false
}
```

---

#### GET `/productos/{idProducto}`
Obtiene un producto por su ID. Público.

**Response `200 OK`:** mismo esquema de objeto producto arriba.

**Response `404 Not Found`:**
```json
{
  "error": "ERROR_NOT_FOUND",
  "mensaje": "Registro no encontrado",
  "status": 404,
  "path": "/productos/f1a2b3c4-...",
  "timestamp": "2026-06-13T10:00:00"
}
```

---

#### GET `/productos/buscar`
Búsqueda por término en nombre y descripción. Público.

**Query params:** `termino=camiseta` `pagina=0` `tamanio=10`

**Response `200 OK`:** mismo esquema paginado.

---

#### GET `/productos/buscar/nombre`
Búsqueda por nombre exacto o parcial. Público.

**Query params:** `nombre=camise` `pagina=0` `tamanio=10`

**Response `200 OK`:** mismo esquema paginado.

---

#### POST `/productos/buscar/avanzado`
Búsqueda filtrada por término y/o categoría. Público.

**Request:**
```json
{
  "termino": "camiseta",
  "categoriaId": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
  "pagina": 0,
  "tamanio": 10
}
```

**Response `200 OK`:** mismo esquema paginado.

---

#### GET `/productos/categorias` 🔒 `ADMIN`
Lista todas las categorías disponibles.

**Response `200 OK`:**
```json
[
  {
    "idCategoria": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f",
    "nombreCategoria": "Ropa"
  },
  {
    "idCategoria": "d2e3f4a5-b6c7-4d8e-9f0a-1b2c3d4e5f6a",
    "nombreCategoria": "Electrónica"
  }
]
```

---

#### POST `/productos` 🔒 `ADMIN`
Crea un nuevo producto. Requiere `multipart/form-data`.

**Content-Type:** `multipart/form-data`

**Campos:**
| Campo | Tipo | Descripción |
|---|---|---|
| `nombreProducto` | String (3–50 chars) | Nombre del producto |
| `descripcionProducto` | String (10–200 chars) | Descripción |
| `precioProducto` | Double (≥ 0) | Precio en pesos colombianos |
| `stockProducto` | Integer (≥ 0) | Unidades disponibles |
| `idCategoria` | UUID | ID de la categoría |
| `imagen` | File | Imagen del producto |

**Response `201 Created`:** objeto producto.

---

#### PUT `/productos/{idProducto}` 🔒 `ADMIN`
Actualiza un producto existente. Requiere `multipart/form-data`.

**Campos:**
| Campo | Tipo | Descripción |
|---|---|---|
| `precioProducto` | Double (≥ 0) | Nuevo precio |
| `stockProducto` | Integer (≥ 0) | Nuevo stock |
| `imagen` | File (opcional) | Nueva imagen |

**Response `200 OK`:** objeto producto actualizado.

---

#### DELETE `/productos/{idProducto}` 🔒 `ADMIN`
Elimina un producto.

**Response `200 OK`:**
```json
{
  "mensaje": "Producto eliminado exitosamente",
  "status": "success"
}
```

---

### Compras — `/compras`

#### GET `/compras/metodo/pago` 🔒 `CLIENTE`
Lista los métodos de pago disponibles.

**Response `200 OK`:**
```json
[
  {
    "idMetodoPago": "m1n2o3p4-q5r6-4s7t-8u9v-0w1x2y3z4a5b",
    "metodoPago": "Wompi"
  }
]
```

---

#### POST `/compras/realizar` 🔒 `CLIENTE`
Crea una nueva compra y procesa el pago con Wompi.

**Headers requeridos:**
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

**Request:**
```json
{
  "idMetodoPago": "m1n2o3p4-q5r6-4s7t-8u9v-0w1x2y3z4a5b",
  "items": [
    {
      "idProducto": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
      "cantidad": 2
    }
  ],
  "wompiTipoPago": "NEQUI",
  "wompiNequiPhone": "3001234567"
}
```

**Request con tarjeta de crédito:**
```json
{
  "idMetodoPago": "m1n2o3p4-q5r6-4s7t-8u9v-0w1x2y3z4a5b",
  "items": [
    {
      "idProducto": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
      "cantidad": 1
    }
  ],
  "wompiTipoPago": "CARD",
  "wompiCardToken": "tok_stagtest_xxxxxxxxxxxx",
  "cuotas": 3
}
```

**Response `201 Created`:**
```json
{
  "idCompra": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "numeroCompra": "ORD-20260613-0001",
  "totalPagado": 99800.0,
  "fechaCompra": "2026-06-13T10:30:00",
  "estado": "PENDIENTE",
  "metodoPago": "Wompi",
  "wompiTransaccionId": "123456-abcdef-789012",
  "asyncPaymentUrl": "https://sandbox.wompi.co/v1/...",
  "detalles": [
    {
      "idProducto": "f1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
      "nombreProducto": "Camiseta Básica",
      "cantidad": 2,
      "precioUnitario": 49900.0,
      "subtotal": 99800.0
    }
  ]
}
```

> Si la misma `Idempotency-Key` se envía más de una vez, la respuesta será idéntica y vendrá con el header `Idempotency-Replayed: true`.

---

#### POST `/compras/mis-compras` 🔒 `CLIENTE`
Lista las compras del usuario autenticado con filtros opcionales.

**Request:**
```json
{
  "numeroCompra": "ORD-20260613-0001",
  "fechaInicio": "2026-01-01T00:00:00",
  "fechaFin": "2026-12-31T23:59:59",
  "estado": "COMPLETADA",
  "pagina": 0,
  "tamanio": 10
}
```

**Response `200 OK`:**
```json
{
  "contenido": [ ... ],
  "numeroPagina": 0,
  "tamanioPagina": 10,
  "totalElementos": 3,
  "totalPaginas": 1,
  "esPrimera": true,
  "esUltima": true
}
```

---

#### GET `/compras/{compraId}/pago/estado` 🔒
Consulta el estado de pago de una compra directamente en Wompi.

**Response `200 OK`:**
```json
{
  "compraId": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
  "numeroCompra": "ORD-20260613-0001",
  "estadoCompra": "PENDIENTE",
  "wompiTransaccionId": "123456-abcdef-789012",
  "estadoWompi": "PENDING",
  "fechaActualizacion": "2026-06-13T10:35:00"
}
```

---

#### DELETE `/compras/{compraId}/cancelar` 🔒 `CLIENTE`
Cancela una compra pendiente del usuario autenticado.

**Response `200 OK`:**
```json
{
  "mensaje": "Compra cancelada exitosamente",
  "status": "success"
}
```

---

#### POST `/compras/admin/todas` 🔒 `ADMIN`
Lista todas las compras de todos los usuarios con filtros.

**Request:** mismo esquema que `/compras/mis-compras`.

**Response `200 OK`:** mismo esquema paginado.

---

#### PUT `/compras/admin/{compraId}/estado` 🔒 `ADMIN`
Actualiza el estado de una compra manualmente.

**Headers requeridos:**
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440001
```

**Request:**
```json
{
  "estado": "COMPLETADA"
}
```

**Response `200 OK`:** objeto compra con el estado actualizado.

---

### Webhook Wompi — `/pagos/wompi`

#### POST `/pagos/wompi/webhook`
Recibe notificaciones de eventos de transacciones desde Wompi. Endpoint público; la autenticidad se verifica internamente con la firma HMAC.

**Headers requeridos:**
```
x-event-checksum: <firma-hmac-sha256>
```

**Request body:** JSON raw del evento Wompi.
```json
{
  "event": "transaction.updated",
  "data": {
    "transaction": {
      "id": "123456-abcdef-789012",
      "status": "APPROVED",
      "reference": "ORD-20260613-0001-b1c2d3e4"
    }
  },
  "timestamp": 1718273400,
  "sent_at": "2026-06-13T10:30:00.000Z"
}
```

**Response `200 OK`** — sin body.

---

### Formato de error estándar

Todos los errores siguen este esquema:

```json
{
  "error": "ERROR_NOT_FOUND",
  "mensaje": "El recurso solicitado no existe",
  "status": 404,
  "path": "/productos/id-inexistente",
  "timestamp": "2026-06-13T10:00:00"
}
```

| Código de error | HTTP | Descripción |
|---|---|---|
| `ERROR_AUTENTICACION` | 401 | Token inválido o ausente |
| `ERROR_AUTORIZACION` | 403 | Sin permisos para el recurso |
| `ERROR_NOT_FOUND` | 404 | Recurso no encontrado |
| `ERROR_CONFLICTO` | 409 | Conflicto (email ya registrado, etc.) |
| `ERROR_VALIDACION` | 400 | Campos inválidos en el request |
| `ERROR_JSON` | 400 | Cuerpo JSON mal formado |
| `ERROR_PARAMETRO` | 400 | Parámetro requerido faltante |
| `ERROR_TIPO_DATO` | 400 | Tipo de dato incorrecto |
| `ERROR_INTERNO` | 500 | Error inesperado del servidor |
