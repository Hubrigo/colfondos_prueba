# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versionado [SemVer](https://semver.org/lang/es/).

## [0.1.0] - 2026-10-22

Primera versión de la HU-CONTACT-001: actualización del correo electrónico del afiliado con validación de identidad.

### Agregado
- **Orquestador** `contact-update-orchestrator` (Spring Boot 3.5, Java 17) con 4 endpoints: iniciar flujo, reenviar OTP, validar OTP y actualizar correo (PR #3).
  - Estado del flujo en el servidor; token de verificación de un solo uso que nunca viaja al navegador.
  - Flujo señuelo anti-enumeración, bloqueo tras 3 OTP, espera de reenvío y rate limiting de 5 flujos por documento y 30 por IP.
  - `Idempotency-Key` en la actualización; la notificación no revierte el cambio (`PENDING`).
  - Resiliencia por proveedor: timeout, reintentos con backoff y circuit breaker.
  - `ProblemDetail` con `code` y `correlationId`; logs JSON (ECS); auditoría sin biometría, OTP ni PII en claro.
  - Configuración validada al arrancar (fail-fast, INC-004).
  - Adaptadores simulados de Customer Data, Identity Capability y Messaging con datos sintéticos (perfil `sim`).
- **Asistente React** `contact-update-wizard` (React 19, Vite 8): identificación, OTP con contador y reenvío, nuevo correo, confirmación y resultado (PR #4).
- **Pruebas:** 55 en el backend (JUnit, Mockito, MockMvc), 72 en el frontend (Vitest) y 26 e2e en Playwright sobre Chromium, Edge, WebKit y `safari17` (PRs #4, #5 y #7).
- **Pipeline CI** con backend, frontend y e2e como checks obligatorios de merge (PR #8).
- **Documentación:** entregables E1–E7, README, plantillas `.env.example` y plantilla de PR (PRs #1, #2, #5, #6, #8 y #9).

### Corregido
- Confirmación trabada en Safari 17.0–17.3: `Promise.withResolvers` y una promesa sin manejo (PR #7, E6).
- Messaging sin circuit breaker, según E2 §5.3 (PR #4).

### Seguridad
- Lint `es-x/restrict-to-es2023` que bloquea APIs posteriores a Safari 17.0 (PR #7).

### Pendiente para producción
- Adaptadores reales de IntegrationHub, almacenamiento compartido (Redis), integración en PortalX 7.4, escaneo de ciberseguridad y certificación en Safari 17 real. Ver E3 §1.2.

[0.1.0]: https://github.com/Hubrigo/colfondos_prueba/releases/tag/v0.1.0
