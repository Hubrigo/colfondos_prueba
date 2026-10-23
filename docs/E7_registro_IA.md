# E7. Registro de uso de IA

Formato según el doc. 15 (*Registro operativo de uso de IA*). Se diligencia un registro por paso de trabajo.
Los commits con código asistido llevan el trailer `AI-Assisted:` para vincularlos a este registro.

**Herramientas:** Claude Code (modelo Claude Opus 5.5) como asistente de desarrollo; ruflo v3.59.17 como memoria del proyecto (namespace `colfondos`, donde se guardan los hallazgos del Mini-RAG para recuperarlos por búsqueda semántica).

---

## IA-00: Preparación del entorno y del repositorio

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | INI-2026-001 / HU-CONTACT-001 |
| Herramienta | Claude Code + ruflo |
| Prompt relevante | "Prepara el entorno y crea el monorepo con backend/, frontend/ y docs/; importa la parte de git: ramas, commits y PR. Valida que ruflo esté instalado." |
| Resultado relevante | Verificación de Java 17, Maven 3.9 y Node 24; instalación de ruflo y de GitHub CLI; monorepo con CONTRIBUTING, plantilla de PR con checklist de incidentes y protección de ramas. |
| Ajustes del usuario | Se detectó que un repositorio descrito en una sesión anterior **no existía** en esta máquina y se rehízo. Ruflo se inicializó **fuera** del repositorio para no mezclar su configuración con el código entregable. |
| Decisión | Aceptado. Git Flow simplificado, PR obligatorio en `main` y `develop`, y solo merge commit. |
| Descartado | Instalar ruflo con `curl \| bash` (script remoto sin revisar); se prefirió `npm install -g`. Commit de la configuración de ruflo dentro del repositorio. |
| Tokens / créditos | No medidos de forma individual (sesión única de Claude Code). |
| Tiempo estimado sin IA | 45 min |
| Tiempo real con IA | 15 min (más la autenticación interactiva del usuario) |
| Observaciones de calidad | El push inicial falló por falta de credenciales; se resolvió con `gh auth login` interactivo, sin exponer tokens en el chat. |

## IA-01: Análisis de la HU (E1)

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | HU-CONTACT-001 |
| Herramienta | Claude Code + ruflo memory (`memory search -q "safari"` para recuperar incidentes) |
| Prompt relevante | "Analiza la HU con los 15 documentos: separa hechos (con cita) de supuestos y recomendaciones; define validaciones de frontend y backend; preguntas para el PO y el LT; criterios en Gherkin." |
| Resultado relevante | Tabla de 15 hechos con su fuente, 7 inconsistencias, 9 supuestos, 14 preguntas y 16 criterios Gherkin. |
| Ajustes del usuario | Se agregó la **tensión central**: enviar el OTP al correo que se quiere cambiar bloquea el flujo. Por eso la validación va por SMS al celular (S-01). También se agregó la discrepancia de alcance correo/celular (H-15) y la de 3 h frente a 4–5 h. |
| Decisión | Aceptado con ajustes. Los valores del OTP (5 min, 3 intentos) se marcan como **supuestos**, no como hechos, porque el Mini-RAG no los define. |
| Descartado | Proponer validación facial como camino principal: no hay proveedor ni contrato (H-02, H-03). |
| Tokens / créditos | No medidos de forma individual. |
| Tiempo estimado sin IA | 60 min |
| Tiempo real con IA | 20 min |
| Observaciones de calidad | Cada hecho se verificó contra el documento citado. Las inferencias que la IA presentaba como hechos se reclasificaron como supuestos. |

## IA-02: Diseño técnico (E2)

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | HU-CONTACT-001 |
| Herramienta | Claude Code + ruflo memory (recuperación de las decisiones de E1: `decision-S01-otp`) |
| Prompt relevante | "Con E1 como base, diseña componentes, contrato de la API del orquestador, diagrama de secuencia, catálogo de errores, resiliencia, seguridad, trazabilidad y variables de ambiente; llena la plantilla de la sección 8." |
| Resultado relevante | 6 decisiones de arquitectura con su alternativa descartada, 4 endpoints, 2 diagramas Mermaid, catálogo de 12 errores, tabla de resiliencia por proveedor, matriz de seguridad OWASP, modelo de auditoría sin biometría y datos sintéticos por escenario. |
| Ajustes del usuario | Se impuso que el token de verificación **no viaje al navegador** (estado en servidor, D-04). Se definió que la actualización solo se reintenta porque es idempotente (`Idempotency-Key`). Se verificó el presupuesto de tiempo de la validación (≈ 6,3 s en el peor caso frente al límite de 60 s). |
| Decisión | Aceptado con ajustes. El diseño es el contrato de los pasos 3 y 4. |
| Descartado | (1) JWT de verificación en el navegador. (2) Reintentar `OTP_INVALID` o errores 4xx. (3) Revertir la actualización si falla Messaging. (4) Wiremock o contenedores para simular: es más pesado de lo que permite el tiempo de la prueba. |
| Tokens / créditos | No medidos de forma individual. |
| Tiempo estimado sin IA | 90 min |
| Tiempo real con IA | 25 min |
| Observaciones de calidad | Se verificó que cada criterio de E1 (CA-01 a CA-16) tenga respaldo en el diseño: errores, datos sintéticos o controles. |

## IA-03: Backend Spring Boot (paso 3, soporte de E2 y E4)

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | HU-CONTACT-001 |
| Herramienta | Claude Code (generación de código y ejecución de build y pruebas de humo) |
| Prompt relevante | "Implementa el contrato de E2 en Spring Boot 3 / Java 17: endpoints, clientes simulados de las 3 APIs, correlationId, manejador global de errores, timeouts, reintentos y circuit breaker por proveedor, y auditoría sin biometría." |
| Resultado relevante | 30 clases en 8 paquetes. Compila y levanta el contexto. La prueba de humo `scripts/smoke-backend.mjs` pasa 22/22 escenarios (CA-01 a CA-16 más idempotencia). Evidencias en `docs/evidencias/`. |
| Ajustes del usuario | La primera prueba de humo **falló en 9 de 22** y reveló 5 defectos, todos corregidos: (1) `spring.mvc.problemdetails.enabled` registraba el manejador de Spring, que le ganaba al nuestro y quitaba `code` y `correlationId`, contra el diseño de un único manejador; (2) un único límite de 5 flujos por IP es irreal con NAT u oficinas, así que se separó en 5 por documento y 30 por IP; (3) el `transactionId` incluía el offset horario; (4) sin variables, el arranque fallaba con "bean no encontrado" en lugar de nombrar la variable (INC-004), y se corrigió con el orden del constructor; (5) las tildes salían dañadas en los logs (cp1252 en Windows), y se fijó `logging.charset.console=UTF-8`. |
| Decisión | Aceptado con correcciones. Resilience4j se aplica de forma **programática** por proveedor (`ResilientCaller`) en lugar de con anotaciones, para controlar qué se reintenta y propagar el MDC al hilo del proveedor. |
| Descartado | (1) Anotaciones `@Retry` y `@TimeLimiter`: requieren AOP y no permiten propagar el correlationId al hilo del time limiter. (2) Spring Security completo: está fuera del alcance de la prueba; los controles quedan documentados en E2 §6. (3) Imprimir el OTP simulado en el log para facilitar la demo: viola la regla de nunca registrar el código, así que se usa `SIM_FIXED_OTP`. |
| Tokens / créditos | No medidos de forma individual. |
| Tiempo estimado sin IA | 4 h |
| Tiempo real con IA | 50 min |
| Observaciones de calidad | La IA generó código que compiló al primer intento, pero **5 defectos solo aparecieron al ejecutar** los escenarios. La prueba de humo contra el servicio levantado fue la validación decisiva. Se verificó por `grep` que los logs no contienen el OTP ni correos en claro. |

## IA-04: Frontend React (paso 4, soporte de E2 y E4)

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | HU-CONTACT-001 |
| Herramienta | Claude Code (código y ejecución de Playwright en Chromium, Edge y WebKit) |
| Prompt relevante | "Implementa el asistente de E2 en React + Vite: identificación, OTP con contador y reenvío, nuevo correo, confirmación y resultado; validaciones en línea, mensaje por cada `code` de error, idempotencia y manejo de todas las promesas (INC-001)." |
| Resultado relevante | Máquina de estados pura (`reducer.js`), 8 componentes de paso, cliente HTTP con correlationId, timeout e idempotencia, reglas de validación compartidas con el backend y ErrorBoundary. Suite e2e de Playwright: **15/15** en Chromium, Edge y WebKit, con capturas. |
| Ajustes del usuario | (1) Se detectó en revisión un **bucle de renders** potencial: el contador despachaba `OTP_EXPIRED_LOCALLY` en cada render; se resolvió con `useCallback` y un reducer idempotente. (2) `crypto.randomUUID` no existe en contextos HTTP no seguros (QA): se agregó alternativa con `getRandomValues`. (3) jsdom 30 exige Node ≥ 24.15 y el entorno tenía 24.14, así que se fijó jsdom 29. (4) La primera corrida e2e **falló 7/15** y destapó: un falso positivo de la propia prueba (Vite sirve `/src/api/…`), estado compartido entre navegadores, un 404 de `favicon.ico` en consola de Edge y un **desvío del diseño en el backend**: Messaging tenía circuit breaker, contra E2 §5.3, y los fallos de un afiliado abrían el circuito para todos. Todos se corrigieron. |
| Decisión | Aceptado con correcciones. Se mantiene la regla de que toda llamada pasa por `run()` con `try/catch`, más `ErrorBoundary` y un listener de `unhandledrejection` como última defensa. |
| Descartado | (1) Librería de formularios o de estado (Formik, Redux): son innecesarias para 5 pasos y aumentan la superficie de compatibilidad. (2) Bloquear el pegado en "confirmar correo": E1 lo desaconseja, pero no lo bloquea, por accesibilidad. (3) Mostrar el `detail` del backend para todos los errores: solo se usa en `OTP_INVALID` y `OTP_RESEND_TOO_SOON`; el resto usa textos propios. |
| Tokens / créditos | No medidos de forma individual. |
| Tiempo estimado sin IA | 3 h |
| Tiempo real con IA | 45 min |
| Observaciones de calidad | Las pruebas e2e en navegadores reales encontraron un defecto del **backend** que la prueba de humo con `fetch` no podía ver, porque solo aparece al encadenar escenarios. Probar en WebKit desde el inicio deja lista la base para E6. |
