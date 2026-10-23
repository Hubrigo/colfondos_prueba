# colfondos_prueba

[![CI](https://github.com/Hubrigo/colfondos_prueba/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Hubrigo/colfondos_prueba/actions/workflows/ci.yml)

Prueba técnica **Desarrollador Fullstack**: Dirección de Canales Digitales.
**HU-CONTACT-001:** *"Como afiliado quiero actualizar mi correo electrónico para recibir comunicaciones en mi nueva dirección."*

Asistente web (React) que guía al afiliado por identificación → OTP por SMS → nuevo correo → confirmación, sobre un orquestador Spring Boot que coordina Customer Data, Identity Capability y Messaging (simuladas), con correlationId, manejo centralizado de errores, resiliencia ante proveedores y trazabilidad sin biometría.

## Entregables

| | Documento |
|---|-----------|
| E1 | [Análisis de la HU](docs/E1_analisis_HU.md) |
| E2 | [Diseño técnico](docs/E2_diseno_tecnico.md) |
| E3 | [Implementación](docs/E3_implementacion.md) |
| E4 | [Pseudocódigo y fragmento representativo](docs/E4_pseudocodigo.md) |
| E5 | [Pruebas](docs/E5_pruebas.md) |
| E6 | [Debug Safari 17](docs/E6_debug_safari.md) |
| E7 | [Registro de uso de IA](docs/E7_registro_IA.md) |

## Estructura

```text
.
├── backend/                     Spring Boot 3.5 · Java 17 · contact-update-orchestrator
│   └── src/main/java/com/colfondos/contactupdate/
│       ├── api/                 Controlador REST y DTOs (Bean Validation)
│       ├── service/             Orquestación, estado del flujo, rate limiting
│       ├── client/ (+ sim/)     Puertos de las APIs corporativas y adaptadores simulados
│       ├── resilience/          Timeout, reintento y circuit breaker por proveedor
│       ├── audit/               Trazabilidad (sin biometría, OTP ni PII en claro)
│       ├── error/               GlobalExceptionHandler (ProblemDetail RFC 7807)
│       ├── observability/       CorrelationIdFilter, enmascaramiento, navegador
│       └── config/              Propiedades validadas al arrancar (fail-fast)
├── frontend/                    React 19 · Vite 8 · contact-update-wizard
│   ├── src/wizard/              Máquina de estados y pasos del asistente
│   ├── src/api/                 Cliente HTTP (correlationId, timeout, idempotencia)
│   ├── src/validation/          Reglas compartidas con el backend
│   └── e2e/                     Playwright (Chromium, Edge, WebKit, safari17)
├── scripts/smoke-backend.mjs    Prueba de humo del API (22 verificaciones)
├── docs/                        Entregables E1–E7 y evidencias
└── .github/                     Pipeline CI y plantilla de PR
```

## Requisitos

| Herramienta | Versión | Verificado con |
|-------------|---------|----------------|
| Java (JDK) | 17+ | 17.0.18 |
| Maven | 3.9+ | 3.9.5 |
| Node.js | 20.19+ o 22.13+ | 24.14.0 |
| Navegadores e2e | `npx playwright install chromium webkit` (Edge usa el instalado) | Playwright 1.64.1 |

## Arranque local

**1. Backend** (perfil `sim`: APIs simuladas y datos sintéticos; no requiere variables)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=sim
# o: mvn package && java -jar target/contact-update-orchestrator-0.1.0-SNAPSHOT.jar --spring.profiles.active=sim
```

Queda en `http://localhost:8080` · salud: `GET /actuator/health`.

**2. Frontend**

```bash
cd frontend
npm ci
npm run dev        # http://localhost:5173  (en dev usa http://localhost:8080 si no hay VITE_API_BASE_URL)
```

**3. Probar**: abrir `http://localhost:5173` y usar un documento sintético. OTP: **`123456`**.

| Documento (CC) | Escenario |
|----------------|-----------|
| `1012345678` | Camino feliz |
| `1000000001` | Customer Data con timeout en el 1.er intento → reintento idempotente (≈ 3 s) |
| `1000000002` | Identity Capability caído → mensaje claro (503) |
| `1000000003` | Messaging caído → correo actualizado, notificación `PENDING` |
| `1000000004` | Correo actual `actual@correo.com` → probar "igual al actual" |
| otro | Afiliado inexistente: misma respuesta (anti-enumeración) |

Trazabilidad de un flujo (solo perfil `sim`): `GET /api/v1/contact-update/flows/{flowId}/audit`.

## Variables de ambiente

Se validan al arrancar: si falta una obligatoria, el servicio **no inicia** y el error nombra la variable (lección de INC-004).

**Backend** (`application.yml`; plantilla `.env` en [E3 §5](docs/E3_implementacion.md#5-configuración-local))

| Variable | Default | Obligatoria fuera de `sim` | Descripción |
|----------|---------|:--------------------------:|-------------|
| `CORS_ALLOWED_ORIGINS` | `sim`: `http://localhost:5173` | **Sí** | Orígenes del portal (lista separada por coma) |
| `PROVIDER_CUSTOMER_DATA_URL` | `sim`: `sim://customer-data` | **Sí** | Customer Data API vía IntegrationHub |
| `PROVIDER_IDENTITY_URL` | `sim`: `sim://identity` | **Sí** | Identity Capability API vía IntegrationHub |
| `PROVIDER_MESSAGING_URL` | `sim`: `sim://messaging` | **Sí** | Messaging API vía IntegrationHub |
| `SERVER_PORT` | `8080` | No | Puerto HTTP |
| `OTP_TTL` | `PT5M` | No | Vigencia del OTP (INC-003) |
| `OTP_MAX_ATTEMPTS` | `3` | No | Intentos antes de bloquear |
| `OTP_RESEND_COOLDOWN` | `PT30S` | No | Espera para reenviar |
| `OTP_MAX_RESENDS` | `3` | No | Reenvíos por flujo |
| `FLOW_TTL` | `PT10M` | No | Vida del flujo en el servidor |
| `RATE_LIMIT_MAX_FLOWS_PER_DOCUMENT` | `5` | No | Flujos por documento por ventana |
| `RATE_LIMIT_MAX_FLOWS_PER_IP` | `30` | No | Flujos por IP por ventana |
| `RATE_LIMIT_WINDOW` | `PT15M` | No | Ventana del limitador |
| `PROVIDER_TIMEOUT` | `PT3S` | No | Timeout de Customer Data e Identity (INC-002) |
| `PROVIDER_MESSAGING_TIMEOUT` | `PT2S` | No | Timeout de Messaging |
| `SIM_FIXED_OTP` | `123456` | **Prohibida** | Solo perfil `sim` |
| `SIM_SLOW_CALL_DELAY` | `PT4S` | **Prohibida** | Solo perfil `sim` (latencia de CA-12) |

**Frontend** (`frontend/.env.example`)

| Variable | Default | Obligatoria | Descripción |
|----------|---------|:-----------:|-------------|
| `VITE_API_BASE_URL` | `http://localhost:8080` solo en `npm run dev` | **Sí** en build | URL del orquestador. Si falta, la app muestra un error de configuración |

> Ejemplo del fail-fast: [`docs/evidencias/backend-failfast-sin-variables.txt`](docs/evidencias/backend-failfast-sin-variables.txt).

## Pruebas

```bash
mvn -f backend verify                  # 55 pruebas (unitarias + MockMvc) + JaCoCo → backend/target/site/jacoco/
cd frontend
npm run verify                         # lint (es-x/compat) + 72 pruebas Vitest + build
npm run test:coverage                  # cobertura v8
npm run test:e2e                       # Playwright en chromium, edge, webkit y safari17 (requiere el jar)
node ../scripts/smoke-backend.mjs      # humo del API (backend con RATE_LIMIT_MAX_FLOWS_PER_DOCUMENT=50)
```

El pipeline [CI](.github/workflows/ci.yml) ejecuta backend, frontend y e2e en cada PR hacia `develop` o `main`.

## API

Base `/api/v1/contact-update/flows` · headers `X-Correlation-Id` (opcional, se devuelve siempre) e `Idempotency-Key` (en la actualización). Contrato completo en [E2 §3](docs/E2_diseno_tecnico.md).

| Método | Ruta | Respuesta |
|--------|------|-----------|
| `POST` | `/` | `202` `{flowId, otpChannel, otpExpiresAt, resendAvailableAt, message}` |
| `POST` | `/{flowId}/otp/resend` | `202` (igual) · `429 OTP_RESEND_TOO_SOON` |
| `POST` | `/{flowId}/otp/verify` | `200 {verified}` · `422 OTP_INVALID/OTP_EXPIRED` · `423 OTP_BLOCKED` |
| `PUT` | `/{flowId}/email` | `200 {transactionId, status, maskedEmail, updatedAt, notificationStatus}` · `403` · `422` · `503/504` |

Los errores siguen RFC 7807 (`application/problem+json`) con `code` y `correlationId`.

## Solución de problemas

| Síntoma | Causa y solución |
|---------|------------------|
| `APPLICATION FAILED TO START … define la URL del proveedor` | Falta una variable obligatoria: usa `--spring.profiles.active=sim` en local o define las de la tabla |
| `429 RATE_LIMITED` al repetir pruebas | Límite de 5 flujos por documento cada 15 min: reinicia el backend o sube `RATE_LIMIT_MAX_FLOWS_PER_DOCUMENT` |
| Tildes dañadas en la consola de Windows | Ya se fuerza UTF-8 (`logging.charset.console`); si persiste, `chcp 65001` |
| `EBADENGINE` con Node < 20.19 | Actualiza Node (Vite 8 y jsdom 29) |

## Flujo de trabajo

Ramas, commits y PR en [CONTRIBUTING.md](CONTRIBUTING.md) · plantilla en [.github/pull_request_template.md](.github/pull_request_template.md).
