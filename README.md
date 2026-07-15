# 🛒 Tienda Online — Backend API

Plataforma e-commerce fullstack construida con **Spring Boot 4.0.5** y **Java 25**. API REST con autenticación JWT (RSA), integración con pasarela de pagos Wompi, rate limiting, caché con Caffeine, y arquitectura lista para producción.

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                        Angular 21 (Frontend)                     │
│               Signals · Standalone · Tailwind · Material         │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTPS / JSON
┌─────────────────────────────▼───────────────────────────────────┐
│                     Nginx (Reverse Proxy)                         │
│              GZIP · Cache · Security Headers · TLS               │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                   Spring Boot 4.0.5 (Backend)                    │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐   │
│  │Controller│→ │ Service  │→ │Repository│→ │  PostgreSQL   │   │
│  └──────────┘  └──────────┘  └──────────┘  └───────────────┘   │
│       ↑                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐   │
│  │  Guards  │  │  Mapper  │  │  Cache   │  │  Bucket4j     │   │
│  │JWT Filter│  │Component │  │ Caffeine │  │ Rate Limiter  │   │
│  └──────────┘  └──────────┘  └──────────┘  └───────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

## 🚀 Stack Tecnológico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| Runtime | Java | 25 |
| Framework | Spring Boot | 4.0.5 |
| Seguridad | Spring Security + JWT (RSA) | jjwt 0.12.6 |
| Base de Datos | PostgreSQL | 17 |
| ORM | Hibernate / JPA | 7.x |
| Cache | Caffeine | 3.1.8 |
| Rate Limiting | Bucket4j | 8.10.1 |
| Validación | Jakarta Bean Validation | 3.x |
| Documentación | SpringDoc OpenAPI (Swagger) | 2.8.6 |
| Build | Maven | 3.9+ |
| Testing | JUnit 5 + jqwik (property-based) | 1.8.4 |

## ✨ Características Principales

- **Autenticación JWT con RSA**: Tokens firmados con clave privada RSA, verificados con pública. Refresh token rotation y blacklist.
- **Rate Limiting por endpoint**: Protección granular con Bucket4j (auth: 10/min, productos: 100/min, compras: 30/min).
- **Cache con Caffeine**: 3 regiones configurables (productos, búsqueda, producto por ID).
- **Idempotencia**: Store con TTL para evitar compras duplicadas.
- **Bloqueo pesimista (PESSIMISTIC_WRITE)**: Previene condiciones de carrera en stock.
- **Integración Wompi**: Pagos con tarjeta, Nequi y Bancolombia Transfer. Webhook con verificación de firma SHA-256.
- **Emails asíncronos**: `@Async` con ThreadPool dedicado para verificación y recuperación.
- **Mappers separados**: Capa de mapeo independiente (Clean Architecture).
- **Global Exception Handler**: Respuestas de error estandarizadas.
- **Paginación server-side**: Consistente en todos los endpoints de listado.
- **BigDecimal para valores monetarios**: Precisión financiera sin errores de redondeo.

## 📖 Documentación API

Con el servidor corriendo, acceder a:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## 🛠️ Decisiones de Diseño

| Decisión | Alternativa | Razón |
|----------|-------------|-------|
| RSA en vez de HMAC para JWT | HMAC-SHA256 | Permite verificar tokens sin compartir el secreto. Preparado para microservicios. |
| Caffeine en vez de Redis | Redis | Simplicidad para single-instance. Redis se añadiría para escalado horizontal. |
| Bucket4j (in-memory) | Spring Cloud Gateway | Menor complejidad. Rate limit distribuido se añadiría con Redis. |
| Signals (Angular) en vez de NgRx | NgRx Store | Menor boilerplate, rendimiento nativo, suficiente para la escala de la app. |
| Property-based testing | Solo unit tests | Demuestra cobertura de edge cases automáticos. |

## ⚡ Quick Start

```bash
# 1. Clonar el repositorio
git clone <repo-url>

# 2. Configurar variables de entorno
cp .env.example .env
# Editar .env con tus credenciales

# 3. Levantar con Docker Compose
docker-compose up -d

# 4. La API estará disponible en http://localhost:8080
# Swagger UI en http://localhost:8080/swagger-ui.html
```

### Desarrollo local (sin Docker)

```bash
# Requiere: Java 25, PostgreSQL 17, Maven 3.9+

# Crear base de datos
createdb tiendaOnline

# Ejecutar con perfil dev (ddl-auto=update, logs detallados)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Ejecutar tests
./mvnw test
```

## 📁 Estructura del Proyecto

```
src/main/java/com/fesc/tiendaOnline/
├── component/          # Componentes reutilizables (IdempotencyStore, JwtBlacklist)
├── config/             # Configuración (Security, Cache, Async, CORS, Wompi)
├── controller/         # REST Controllers (thin layer)
├── exception/          # Excepciones personalizadas + GlobalExceptionHandler
├── mapper/             # Mappers Entity ↔ DTO (Clean Architecture)
├── model/
│   ├── dto/            # Data Transfer Objects (request/response)
│   └── entity/         # Entidades JPA
├── repository/         # Spring Data JPA Repositories
├── security/           # UserDetailsImpl + CustomUserDetailsService
└── service/            # Lógica de negocio
```

## 🔐 Endpoints Principales

| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| POST | `/auth/login` | Público | Iniciar sesión |
| POST | `/auth/refresh` | Público | Renovar access token |
| POST | `/auth/logout` | Autenticado | Cerrar sesión (blacklist JWT) |
| GET | `/productos` | Público | Listar productos (paginado) |
| POST | `/productos` | ADMIN | Crear producto |
| POST | `/compras/realizar` | CLIENTE | Realizar compra (idempotente) |
| POST | `/reviews` | CLIENTE | Crear/actualizar reseña |
| POST | `/pagos/wompi/webhook` | Público | Webhook de Wompi |

## 🧪 Testing

```bash
# Unit + Integration tests
./mvnw test

# Property-based tests (jqwik)
./mvnw test -Dtest="*Property*"
```

## 📄 Licencia

Proyecto personal de portafolio. Todos los derechos reservados.
