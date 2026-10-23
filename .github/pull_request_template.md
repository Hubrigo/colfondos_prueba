## Descripción
<!-- Qué cambia y por qué. -->

Refs: HU-CONTACT-001

## Tipo de cambio
- [ ] feat
- [ ] fix
- [ ] test
- [ ] docs
- [ ] refactor / chore

## Evidencias
- [ ] Pruebas unitarias en verde (`mvn test`, `npm test`)
- [ ] Pruebas de integración (MockMvc) en verde
- [ ] Capturas o logs adjuntos en `docs/evidencias/`

## Checklist de lecciones aprendidas (doc. 13)
- [ ] **INC-001 Safari**: probado en Chrome, Edge y Safari, o con la prueba de compatibilidad; sin APIs JS no soportadas y con promesas con `catch`.
- [ ] **INC-002 Timeouts**: llamadas a proveedores con timeout y reintentos acotados.
- [ ] **INC-003 OTP**: vigencia del OTP coherente con el flujo menor a 3 min y mensaje claro al expirar.
- [ ] **INC-004 Variables de ambiente**: toda variable nueva está en `.env.example`, `application.yml` y el README.
- [ ] **Biometría (doc. 08)**: no se almacenan ni registran imágenes o datos faciales.
- [ ] **Observabilidad (doc. 11)**: `correlationId` propagado y logs estructurados sin PII en claro.

## Uso de IA (doc. 15)
<!-- Herramienta, prompt relevante, qué se aceptó, qué se corrigió y qué se descartó. -->
