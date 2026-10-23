# E5. Pruebas

**HU-CONTACT-001.** Estrategia, matriz de pruebas y resultados. Los tipos de prueba siguen el doc. 09 (funcionales, negativas, integración, regresión, compatibilidad de navegador e indisponibilidad de servicios externos), y cada caso se cruza con los criterios de aceptación de [E1](E1_analisis_HU.md) (CA-xx) y con los incidentes del doc. 13 (INC-xxx).

---

## 1. Estrategia (pirámide)

| Nivel | Herramienta | Qué valida | Dónde |
|-------|-------------|------------|-------|
| Unitarias backend | JUnit 5 + Mockito + AssertJ | Reglas de la HU en `ContactUpdateService` con proveedores mockeados y reloj controlable; política de resiliencia real; enmascaramiento; fail-fast de configuración | `backend/src/test/java/**` |
| Integración backend | Spring Boot Test + **MockMvc** (perfil `sim`) | Contrato HTTP de E2 §3, formato ProblemDetail, `correlationId` y escenarios de proveedor sobre los adaptadores simulados | `ContactUpdateControllerIntegrationTest` |
| Unitarias frontend | **Vitest** + Testing Library + jsdom | Validaciones, fechas, UUID, cliente HTTP (timeouts, errores), reducer y asistente completo con API falsa | `frontend/src/**/*.test.{js,jsx}` |
| Estático | **ESLint** (`es-x/restrict-to-es2023` + `compat/compat`) | APIs de ECMAScript y web no disponibles en Safari 17 / Chrome / Edge 111 (prevención de INC-001) | `frontend/eslint.config.js` (`npm run lint`) |
| End-to-end | **Playwright** | Flujo real navegador → Vite → backend `sim` en **Chromium, Edge, WebKit y `safari17`** (WebKit con la línea base de JS de Safari 17.0, ver E6); falla ante cualquier error en consola | `frontend/e2e/*.spec.js` |
| Humo de API | Node (`fetch`) | 22 verificaciones contra el servicio levantado (útil para la sustentación) | `scripts/smoke-backend.mjs` |

**Criterio de salida propuesto (DoD de pruebas):** las 4 suites en verde, cobertura de líneas ≥ 80 % en backend y frontend, lint sin errores, e2e en los 4 proyectos sin errores de consola, y la ejecución manual en Safari 17 real (§5) firmada por QA.

---

## 2. Resultados

| Suite | Comando | Resultado | Evidencia |
|-------|---------|-----------|-----------|
| Backend: unitarias + integración | `mvn -f backend test` | **55/55** ✅ (6 clases) | `docs/evidencias/backend-pruebas.txt` |
| Cobertura backend (JaCoCo) | `target/site/jacoco/index.html` | **94,8 % líneas · 76,3 % ramas** | `docs/evidencias/backend-cobertura-jacoco.txt` |
| Frontend: Vitest | `npm test` | **72/72** ✅ (6 archivos) | `docs/evidencias/frontend-vitest.txt` |
| Cobertura frontend (v8) | `npm run test:coverage` | **92,9 % líneas · 91,8 % ramas** | idem |
| E2E en 4 proyectos | `npm run test:e2e` | **26/26** ✅ (6 escenarios × Chromium, Edge, WebKit y safari17 + 2 de bisección en WebKit) | `docs/evidencias/frontend-e2e-navegadores.txt` + capturas |
| Humo de API | `node scripts/smoke-backend.mjs` | **22/22** ✅ | `docs/evidencias/backend-smoke-sim.txt` |

Detalle del backend por clase:

| Clase | Pruebas | Foco |
|-------|---------|------|
| `ContactUpdateServiceTest` (3 `@Nested`) | 19 | Identificación, OTP, actualización y notificación |
| `ContactUpdateControllerIntegrationTest` | 12 | Contrato HTTP con MockMvc |
| `ObservabilityTest` | 14 | Enmascaramiento y detección de navegador (parametrizadas) |
| `ResilientCallerTest` | 6 | Reintento, timeout, circuit breaker y MDC |
| `ContactUpdatePropertiesTest` | 3 | Fail-fast de variables (INC-004) |
| `ContactUpdateApplicationTests` | 1 | Arranque del contexto |

---

## 3. Matriz de pruebas

Leyenda de nivel: **U-B** unitaria backend · **I-B** integración backend (MockMvc) · **U-F** unitaria frontend (Vitest) · **E2E** Playwright · **M** manual.

### 3.1 Funcionales (camino feliz)

| ID | Escenario | CA / INC | Nivel | Prueba automatizada | Resultado |
|----|-----------|----------|-------|---------------------|-----------|
| F-01 | Flujo completo: identificación → OTP → correo → confirmación → resultado | CA-01 | U-B, I-B, U-F, E2E | `actualizacionExitosa`, `flujoCompleto`, `CA-01: completa el flujo…`, `CA-01 / CA-15` | ✅ |
| F-02 | Se notifica al correo nuevo **y** se avisa al anterior | CA-01, S-06 | U-B | `actualizacionExitosa` (verifica las 2 plantillas y sus destinatarios) | ✅ |
| F-03 | Trazabilidad: FLOW_STARTED, OTP_SENT, OTP_VERIFIED, EMAIL_UPDATED, NOTIFICATION_SENT | CA-01 | I-B | `flujoCompleto` (consulta `/audit`) | ✅ |
| F-04 | El mismo `correlationId` viaja en todas las llamadas y en la auditoría | CA-16 | I-B, U-F, U-B | `flujoCompleto`, `CA-01…mismo correlationId`, `propagaCorrelationId` | ✅ |
| F-05 | Reenvío del OTP sin perder el paso | CA-06 | U-B, U-F | `reenvioAntesDeLaEspera…`, `CA-06: un OTP vencido…` | ✅ |

### 3.2 Negativas

| ID | Escenario | CA | Nivel | Prueba automatizada | Resultado |
|----|-----------|----|-------|---------------------|-----------|
| N-01 | Documento con formato inválido: no se llama al backend | CA-02 | U-F, E2E, I-B | `validateDocumentNumber`, `CA-02: no llama al backend…`, `validacionDevuelveProblemDetail` | ✅ |
| N-02 | Documento inexistente responde igual que uno real (anti-enumeración) | CA-03 | U-B, humo | `documentoInexistenteNoRevelaExistencia` | ✅ |
| N-03 | OTP incorrecto informa los intentos restantes | CA-04 | U-B, I-B, U-F, E2E | `otpIncorrectoInformaIntentos`, `otpIncorrectoYBloqueo`, `CA-04` | ✅ |
| N-04 | 3 OTP fallidos bloquean, incluso con el código correcto después | CA-05 | U-B, I-B, U-F | `sinIntentosBloquea`, `otpIncorrectoYBloqueo`, `CA-05` | ✅ |
| N-05 | OTP expirado | CA-06 | U-B, U-F | `otpExpirado`, `CA-06` | ✅ |
| N-06 | Aviso preventivo de expiración (≤ 30 s) | CA-07 | U-F | `CA-07: avisa cuando quedan 30 segundos…` | ✅ |
| N-07 | Correo inválido (`a@b`, sin arroba, sin dominio, > 254) | CA-08 | U-F, I-B | `validateEmail`, `validacionesDeCorreo` | ✅ |
| N-08 | Correo igual al actual (sin importar mayúsculas) | CA-08 | U-B, humo | `correoIgualAlActual` | ✅ |
| N-09 | Confirmación distinta | CA-09 | U-F, I-B | `validateEmailConfirmation`, `validacionesDeCorreo` | ✅ |
| N-10 | Reenvío antes de 30 s y más de 3 reenvíos | S-03 | U-B | `reenvioAntesDeLaEsperaSeRechazaYDespues…` | ✅ |
| N-11 | Flujo vencido o inexistente → 404 | — | U-B, I-B | `flujoVencidoNoExiste`, `flujoInexistente` | ✅ |
| N-12 | JSON con propiedades desconocidas o enum inválido → 400 | H-09 | I-B | `rechazaPropiedadesDesconocidasYTiposInvalidos` | ✅ |
| N-13 | Falta `Idempotency-Key` | — | U-B, I-B | `claveDeIdempotenciaInvalidaSeRechaza`, `faltaIdempotencyKey` | ✅ |

### 3.3 Seguridad

| ID | Escenario | Control | Nivel | Prueba automatizada | Resultado |
|----|-----------|---------|-------|---------------------|-----------|
| S-01 | Actualizar sin OTP validado → 403 y no llama a Customer Data | CA-10, OWASP A01 | U-B, I-B, humo | `sinValidacionDeIdentidad`, `sinIdentidadValidada` | ✅ |
| S-02 | Validación vencida exige validar de nuevo | CA-10 | U-B | `validacionVencidaExigeNuevaValidacion` | ✅ |
| S-03 | Token de verificación de un solo uso | D-04 | U-B | `tokenEsDeUnSoloUso` | ✅ |
| S-04 | Rate limiting: el 6.º flujo del mismo documento → 429 | OWASP A04 | U-B | `sextoIntentoDelMismoDocumentoEsLimitado` | ✅ |
| S-05 | Auditoría sin OTP ni correo en claro | CA-11, doc. 08 | I-B, humo | `flujoCompleto` (aserción CA-11) | ✅ |
| S-06 | PII enmascarada (correo, documento, celular); referencia de afiliado no reversible | doc. 08 | U-B | `ObservabilityTest` | ✅ |
| S-07 | Un `X-Correlation-Id` con CRLF se reemplaza (inyección en logs) | OWASP A09 | I-B | `correlationIdInseguroSeReemplaza` | ✅ |
| S-08 | Errores sin stack trace ni detalle del proveedor | E2 §5.1 | I-B | `validacionDevuelveProblemDetail`, `proveedorDeIdentidadCaido` | ✅ |
| S-09 | Doble envío de la confirmación imposible mientras hay solicitud en curso | E1 §3.1 | U-F | `el botón Confirmar se deshabilita…` | ✅ |
| S-10 | Escaneo de código seguro (3 días hábiles) | doc. 08 | M | Fuera del alcance de la prueba: planificado en E3 | ⏳ |

### 3.4 Proveedor externo (indisponibilidad y degradación)

| ID | Escenario | CA / INC | Nivel | Prueba automatizada | Resultado |
|----|-----------|----------|-------|---------------------|-----------|
| P-01 | Timeout transitorio de Customer Data: reintento idempotente → 200 | CA-12, INC-002 | U-B, I-B, humo | `reintentaFallaTransitoria`, `timeoutTransitorioConReintento` | ✅ (≈ 3,2 s con timeouts productivos) |
| P-02 | Timeout persistente: se agotan los 3 intentos → 504 `PROVIDER_TIMEOUT` | INC-002 | U-B | `timeoutAgotaReintentos` (también verifica la métrica `outcome=timeout`) | ✅ |
| P-03 | Identity Capability caído → 503 con mensaje claro | CA-13, INC-005 | U-B, I-B, U-F, E2E | `proveedorCaidoSePropaga`, `proveedorDeIdentidadCaido`, `CA-13` | ✅ |
| P-04 | Proveedor degradado: el circuito se abre y falla rápido, sin llamar al proveedor | INC-005 | U-B | `circuitoSeAbreAnteDegradacion` | ✅ |
| P-05 | Messaging caído: actualización `UPDATED` + notificación `PENDING` | CA-14 | U-B, I-B, U-F, E2E | `messagingCaidoNoRevierte`, `messagingCaido`, `CA-14` | ✅ |
| P-06 | Messaging **sin** circuit breaker (los fallos de un afiliado no afectan a otros) | E2 §5.3 | U-B, E2E | `messagingSinCircuitBreaker` + e2e encadenado en 3 navegadores | ✅ |
| P-07 | Un rechazo de negocio del proveedor (403) no se reintenta | E2 §5.3 | U-B | `errorDeNegocioNoSeReintenta` | ✅ |
| P-08 | Red caída, 502 no JSON o timeout del cliente se presentan como mensajes de dominio | INC-002 | U-F | `client.test.js` (5 casos) | ✅ |
| P-09 | Reintento del cliente tras un timeout con la **misma** `Idempotency-Key` | CA-12 | U-F | `CA-12: tras un timeout del cliente…` | ✅ |

### 3.5 Compatibilidad de navegador (Safari)

| ID | Escenario | CA / INC | Nivel | Prueba automatizada | Resultado |
|----|-----------|----------|-------|---------------------|-----------|
| C-01 | Flujo completo sin errores JS en consola en **Chromium, Edge y WebKit** | CA-15, INC-001 | E2E | `CA-01 / CA-15` × 3 proyectos | ✅ |
| C-02 | Un rechazo inesperado (`TypeError`) se captura y no queda como *Unhandled Promise Rejection* | INC-001 | U-F | `INC-001: un rechazo inesperado…` | ✅ |
| C-03 | UUID sin `crypto.randomUUID` (contexto HTTP no seguro) | INC-001 | U-F | `uuid › funciona sin crypto.randomUUID` | ✅ |
| C-04 | Fecha inválida o en formato no ISO: `formatDateTime` no lanza | INC-001 | U-F | `una fecha inválida (…) no lanza` | ✅ |
| C-05 | Detección de Safari, Edge y Chrome para el indicador "errores por navegador" | doc. 11 | U-B | `detectaNavegador` | ✅ |
| C-06 | **Safari 17 real** (macOS e iOS): flujo completo, consola limpia, autocompletado del OTP por SMS | CA-15 | **M** | Ver §5: Playwright WebKit **no es** Safari 17 | ⏳ QA |
| C-07 | Defecto de Safari 17 reportado por QA (confirmación con HTTP 200 y *Promise rejected*) | INC-001 | E2E + U-F | `safari17.spec.js` (4 proyectos), `timing.test.js`, `E6: en Safari 17.0…` (ver E6) | ✅ |
| C-08 | Uso de built-ins de ES2024+ (p. ej. `Promise.withResolvers`) bloqueado por lint | INC-001 | Estático | `npm run lint`: falla con la versión defectuosa (`e6-6-lint-detecta-defecto.txt`) | ✅ |

### 3.6 Regresión

| ID | Qué protege | Origen | Prueba automatizada |
|----|-------------|--------|---------------------|
| R-01 | Errores JS en Safari | INC-001 | C-01 a C-05, C-07, C-08 (E6) |
| R-02 | Timeouts con proveedor síncrono | INC-002 | P-01, P-02, P-08 |
| R-03 | OTP que expira antes de terminar | INC-003 | N-05, N-06, F-05 y la vigencia de 5 min (S-03) en `afiliadoActivoRecibeOtpPorSms…` |
| R-04 | Variable de ambiente faltante | INC-004 | `ContactUpdatePropertiesTest` (3) + evidencia `backend-failfast-sin-variables.txt` |
| R-05 | Degradación del proveedor de identidad | INC-005 | P-03, P-04 |
| R-06 | Un solo manejador de errores (`code` y `correlationId` siempre presentes) | IA-03, defecto 1 | `validacionDevuelveProblemDetail` |
| R-07 | Circuit breaker de Messaging | IA-04 | P-06 |
| R-08 | Módulo *Actualización de Datos* de PortalX 7.0 (si se integra allí) | doc. 02 | **M**: regresión del portal a cargo de QA (L-01) |

### 3.7 No funcionales

| ID | Escenario | Meta | Nivel | Resultado |
|----|-----------|------|-------|-----------|
| NF-01 | Validación de identidad en el peor caso con proveedor lento | < 60 s (doc. 14) | Cálculo + U-B | 3 s × 2 + 0,3 s ≈ **6,3 s** ✅ |
| NF-02 | Flujo completo | < 3 min (doc. 14) | E2E | 0,7–1,5 s sin tiempo de usuario ✅ |
| NF-03 | Accesibilidad básica: etiquetas, `aria-invalid`, `role=alert` y `timer` | — | U-F, E2E | Las pruebas localizan por rol y etiqueta ✅ |
| NF-04 | Carga (volumen transaccional) | Pendiente: dato de negocio (P-07) | M | ⏳ Sin volumen definido |

---

## 4. Datos de prueba

Datos sintéticos de E2 §9 (perfil `sim`). OTP fijo `123456` (`SIM_FIXED_OTP`).

| Documento (CC) | Escenario |
|----------------|-----------|
| 1012345678 | Camino feliz |
| 1000000001 | Timeout en el 1.er intento de actualización |
| 1000000002 | Identity Capability caído |
| 1000000003 | Messaging caído |
| 1000000004 | Correo actual `actual@correo.com` |
| Cualquier otro | Afiliado inexistente (flujo señuelo) |

---

## 5. Limitaciones y pruebas manuales pendientes

- **Playwright WebKit ≠ Safari 17.** Es el mismo motor (WebKit), pero en otra versión y en Windows. Sirve para detectar regresiones temprano y para reproducir el defecto de E6, pero **no sustituye** la certificación en Safari 17 real en macOS e iOS (granja de dispositivos o equipo físico). QA debe ejecutar C-06 y adjuntar la evidencia (doc. 09).
- **Adaptadores simulados.** Las pruebas validan el contrato de E2, no el de las APIs reales de IntegrationHub (aún no documentado, L-03). Cuando existan, se agregan pruebas de contrato contra QA.
- **Estado en memoria.** `FlowStore` y la auditoría en memoria no prueban el comportamiento multi-instancia (Redis o almacén corporativo, L-05).
- **Carga.** No hay volumen transaccional definido (P-07); la prueba de carga queda pendiente.

## 6. Cómo ejecutar

```bash
# Backend: unitarias + integración + cobertura
mvn -f backend test            # reporte: backend/target/site/jacoco/index.html

# Frontend: unitarias + cobertura
cd frontend && npm test && npm run test:coverage

# E2E en Chromium, Edge y WebKit (requiere el jar del backend)
mvn -f ../backend package -DskipTests && npx playwright install chromium webkit && npm run test:e2e
```
