## Descripción
<!-- Qué cambia y por qué. Si corrige un defecto: causa raíz y cómo se reprodujo. -->

Refs: HU-CONTACT-001

## Tipo de cambio
- [ ] feat
- [ ] fix
- [ ] test
- [ ] docs
- [ ] refactor / chore / build / ci

## Evidencias
- [ ] **CI en verde** (backend · frontend · e2e)
- [ ] `mvn -f backend verify` en verde (unitarias + MockMvc)
- [ ] `npm run verify` en verde (lint + Vitest + build)
- [ ] `npm run test:e2e` en verde si cambia la UI o el contrato
- [ ] Capturas o logs adjuntos en `docs/evidencias/` cuando aplique

## Checklist de lecciones aprendidas (doc. 13)
- [ ] **INC-001 Safari**: e2e en Chrome, Edge, WebKit y `safari17`; sin APIs posteriores a Safari 17.0 (lo vigila el lint `es-x`); toda llamada asíncrona pasa por `run()` o tiene `catch`.
- [ ] **INC-002 Timeouts**: llamadas a proveedores con timeout y reintentos acotados; solo se reintenta lo idempotente.
- [ ] **INC-003 OTP**: vigencia coherente con el flujo menor a 3 min y mensaje claro al expirar.
- [ ] **INC-004 Variables de ambiente**: toda variable nueva está en `.env.example`, `application.yml`, `ContactUpdateProperties` (validada) y el README.
- [ ] **Biometría (doc. 08)**: no se almacenan ni registran imágenes o datos faciales.
- [ ] **Observabilidad (doc. 11)**: `correlationId` propagado y logs estructurados sin PII en claro.

## Despliegue (doc. 12)
- [ ] ¿Requiere comité de cambios (martes 11:00 AM)?
- [ ] ¿Requiere escaneo de ciberseguridad (3 días hábiles, doc. 08)?
- [ ] Variables nuevas comunicadas a Operación TI antes de la ventana de las 9:00 PM

## Uso de IA (doc. 15)
<!-- Herramienta, prompt relevante, qué se aceptó, qué se corrigió y qué se descartó. Registro IA-xx en docs/E7_registro_IA.md. -->
