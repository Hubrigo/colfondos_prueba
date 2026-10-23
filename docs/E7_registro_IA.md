# E7. Registro de uso de IA

Formato según el doc. 15 (*Registro operativo de uso de IA*): un registro por paso de trabajo (IA-00 a IA-10). Los commits con código asistido llevan el trailer `AI-Assisted:` (13 commits) para vincularlos a este registro.

## 1. Herramientas

| Herramienta | Uso |
|-------------|-----|
| **Claude Code** (modelo Claude Opus 5.5) | Asistente de desarrollo: análisis del Mini-RAG, redacción de documentos, generación de código y pruebas, y **ejecución** de build, pruebas, Playwright, git y GitHub CLI |
| **ruflo** v3.59.17 | Memoria del proyecto (namespace `colfondos`): hallazgos del Mini-RAG y decisiones por paso, recuperados por búsqueda semántica (p. ej. `memory search -q "safari"`) |
| GitHub CLI + Actions | PRs, protección de ramas y pipeline CI |

## 2. Cómo leer este registro (atribución)

El doc. 15 pide "ajustes realizados por el usuario". Para que la atribución sea honesta, cada registro separa:

- **Decisión del candidato:** lo que yo definí, pedí, aprobé o rechacé.
- **Detectado al validar:** correcciones que surgieron al **ejecutar** compilación, pruebas, navegadores o CI. La IA las encontró y las propuso, y yo las revisé y acepté. No las presento como hallazgos míos, sino como resultado del ciclo *generar → ejecutar → verificar*, que fue la forma de trabajo que exigí desde el inicio.

**Tiempos:**
- *Tiempo real con IA*: medido con los timestamps de los commits de cada paso; incluye la espera de mis aprobaciones entre pasos.
- *Tiempo estimado sin IA*: estimación propia para el mismo alcance.

---

## 3. Resumen

| ID | Paso | Tiempo real (git) | Estimado sin IA | Defectos detectados al validar |
|----|------|------------------:|----------------:|-------------------------------:|
| IA-00 | Entorno y repositorio | ≈ 15 min | 45 min | 1 (credenciales) |
| IA-01 | E1, análisis de la HU | ≈ 10 min | 60 min | — |
| IA-02 | E2, diseño técnico | ≈ 6 min | 90 min | — |
| IA-03 | Backend | ≈ 23 min | 4 h | 5 |
| IA-04 | Frontend + e2e | ≈ 16 min | 3 h | 7 |
| IA-05 | E5, pruebas | ≈ 14 min | 4 h | 5 (en las pruebas) |
| IA-06 | E4, pseudocódigo | ≈ 5 min | 45 min | — |
| IA-07 | E6, debug de Safari 17 | ≈ 14 min | 3 h | 3 (premisa, herramienta y evidencia) |
| IA-08 | E3 + CI | ≈ 8 min (+ CI) | 3 h | 1 (permiso `.env.*`) |
| IA-09 | E7, consolidación | ≈ 10 min | 45 min | 1 (tiempos inflados) |
| IA-10 | Entrega: Word, release y zip | ≈ 50 min (≈ 25 de espera del CI) | 2 h | 4 |
| **Total** | | **≈ 2 h 50 min** (171 min) | **≈ 24 h** (1 425 min) | **27** |

> La reducción de tiempo es real, pero **no** viene de que la IA acierte al primer intento. El código compiló casi siempre, y aun así aparecieron **27 defectos que solo se vieron al ejecutar**. Sin el ciclo de validación, varios habrían llegado a QA, como pasó con el de Safari 17 en E6.

**Tokens / créditos:** no se midieron por paso. Al cierre, `/cost` en Claude Code (plan de suscripción) no reporta tokens, sino el uso del límite del plan: **80 % del límite de la sesión** y **12 % del límite semanal**. Se registra tal cual, sin estimar tokens.

---

## 4. Registros

### IA-00: Preparación del entorno y del repositorio

| Campo | Registro |
|-------|----------|
| Iniciativa / HU | INI-2026-001 / HU-CONTACT-001 |
| Herramienta | Claude Code + ruflo + GitHub CLI |
| Prompt relevante | "Prepara el entorno y crea el monorepo con backend/, frontend/ y docs/; es importante la parte de git: ramas, commits y PR. Valida que ruflo esté instalado; si no, instálalo y úsalo." |
| Resultado relevante | Verificación de Java 17, Maven 3.9 y Node 24; instalación de ruflo y de GitHub CLI; monorepo con CONTRIBUTING, plantilla de PR con checklist de incidentes y protección de ramas. |
| Decisión del candidato | Stack (Java 17, Spring Boot 3, React + Vite) y entrega en .docx + .zip. El repositorio como evidencia de E3 (ramas por paso, commits convencionales, PR). Repositorio en GitHub, **público**. Protección de `main` y `develop`. **Commits sin `Co-Authored-By`** (desde el paso 2; los 4 commits iniciales se conservan para no reescribir ramas protegidas). |
| Detectado al validar | El repositorio descrito en una sesión anterior **no existía** en esta máquina y se rehízo. El primer push falló por falta de credenciales; se resolvió con `gh auth login` interactivo, sin exponer tokens en el chat. |
| Decisión | Aceptado. Ruflo se inicializa **fuera** del repositorio, para no mezclar su configuración con el código entregable. |
| Descartado | Instalar ruflo con `curl \| bash` (script remoto sin revisar); se usó `npm install -g`. |
| Tiempo real / estimado sin IA | ≈ 15 min (más la autenticación) / 45 min |
| Observaciones de calidad | La IA no asumió el estado descrito en la conversación anterior: lo verificó en disco. |

### IA-01: Análisis de la HU (E1)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + ruflo memory |
| Prompt relevante | "Analiza la HU con los 15 documentos: separa hechos (con cita) de supuestos y recomendaciones; define validaciones de frontend y backend, preguntas para el PO y el LT, y criterios en Gherkin." |
| Resultado relevante | 15 hechos con su fuente, 7 inconsistencias, 9 supuestos, 14 preguntas y 16 criterios Gherkin. |
| Decisión del candidato | Mi análisis previo del Mini-RAG ya identificaba los hallazgos base: PortalX 7.0 frente a 7.4, OnPrem frente a Cloud, identidad sin flujo descrito, proveedor facial indefinido, Messaging sin reintentos ni SLA, INC-001 a INC-004, las restricciones de tiempo y biometría, y la diferencia de 3 h frente a 4–5 h. Aprobé el análisis resultante. |
| Detectado al validar | La IA propuso la **tensión central**: enviar el OTP al correo que se quiere cambiar bloquea el flujo, de ahí S-01 (SMS al celular). También detectó la discrepancia de alcance entre correo y celular (H-15). Al revisar se reclasificaron como **supuestos** valores que la IA había presentado como hechos (OTP de 5 min y 3 intentos). |
| Decisión | Aceptado con ajustes. |
| Descartado | Validación facial como camino principal: no hay proveedor ni contrato (H-02, H-03). |
| Tiempo real / estimado sin IA | ≈ 10 min / 60 min |
| Observaciones de calidad | Cada hecho se verificó contra el documento citado. |

### IA-02: Diseño técnico (E2)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + ruflo memory (`decision-S01-otp`) |
| Prompt relevante | "Con E1 como base, diseña componentes, contrato de la API, diagrama de secuencia, catálogo de errores, resiliencia, seguridad, trazabilidad y variables; llena la plantilla de la sección 8." |
| Resultado relevante | 6 decisiones de arquitectura con su alternativa descartada, 4 endpoints, 2 diagramas Mermaid, 12 errores, resiliencia por proveedor, OWASP, auditoría sin biometría y datos sintéticos. |
| Decisión del candidato | Aprobé el diseño como contrato de los pasos 3 y 4. |
| Detectado al validar | Las decisiones de diseño las propuso la IA: el token de verificación queda en el servidor (D-04), la actualización solo se reintenta porque es idempotente y el presupuesto de la validación es ≈ 6,3 s frente a 60 s. Se verificó que cada criterio CA-01 a CA-16 tenga respaldo en el diseño. |
| Decisión | Aceptado. |
| Descartado | JWT en el navegador; reintentar 4xx u `OTP_INVALID`; revertir la actualización si falla Messaging; Wiremock o contenedores (demasiado para el tiempo de la prueba). |
| Tiempo real / estimado sin IA | ≈ 6 min / 90 min |
| Observaciones de calidad | Una regla de este diseño (Messaging **sin** circuit breaker) no se respetó en el código, y lo descubrieron las pruebas e2e en IA-04. Un diseño escrito no garantiza la implementación. |

### IA-03: Backend Spring Boot (soporte de E2 y E4)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code (código, build y prueba de humo contra el servicio levantado) |
| Prompt relevante | "Implementa el contrato de E2 en Spring Boot 3 / Java 17: endpoints, 3 APIs simuladas, correlationId, manejador global de errores, timeouts, reintentos, circuit breaker y auditoría sin biometría." |
| Resultado relevante | 30 clases en 8 paquetes; humo 22/22; cada commit compila por separado. |
| Decisión del candidato | Aprobé el paso y los commits por capa. |
| Detectado al validar | La primera prueba de humo **falló en 9 de 22** y mostró 5 defectos: (1) `spring.mvc.problemdetails` anulaba el manejador propio (sin `code` ni `correlationId`); (2) un límite único de 5 flujos por IP es irreal con NAT, así que se separó en 5 por documento y 30 por IP; (3) el `transactionId` incluía el offset horario; (4) sin variables, el error hablaba de un bean faltante en vez de la variable (INC-004); (5) las tildes salían dañadas en los logs (cp1252). |
| Decisión | Aceptado con correcciones. Resilience4j programático (`ResilientCaller`). |
| Descartado | Anotaciones `@Retry` y `@TimeLimiter` (no propagan el MDC); Spring Security completo (fuera del alcance); imprimir el OTP simulado en el log. |
| Tiempo real / estimado sin IA | ≈ 23 min / 4 h |
| Observaciones de calidad | El código compiló al primer intento: los 5 defectos solo aparecieron al ejecutar. Con `grep` se verificó que los logs no contienen el OTP ni correos en claro. |

### IA-04: Frontend React + e2e (soporte de E2 y E4)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + Playwright (Chromium, Edge y WebKit) |
| Prompt relevante | "Implementa el asistente de E2 en React + Vite con validaciones, contador del OTP, mensajes por `code`, idempotencia y manejo de todas las promesas (INC-001)." |
| Resultado relevante | Máquina de estados, 8 componentes, cliente HTTP con correlationId, timeout e idempotencia, y e2e 15/15 en 3 navegadores. |
| Decisión del candidato | Aprobé el paso. |
| Detectado al validar | En revisión: un **bucle de renders** potencial en el contador y la ausencia de `crypto.randomUUID` en contextos HTTP. En la instalación: jsdom 30 exige Node ≥ 24.15, así que se fijó jsdom 29. La primera corrida e2e **falló 7/15**: un falso positivo de la propia prueba, estado compartido entre navegadores, un 404 de `favicon.ico` en la consola de Edge y un **desvío del diseño en el backend** (Messaging con circuit breaker). |
| Decisión | Aceptado con correcciones. |
| Descartado | Formik o Redux; bloquear el pegado en la confirmación; mostrar el `detail` del backend en todos los errores. |
| Tiempo real / estimado sin IA | ≈ 16 min / 3 h |
| Observaciones de calidad | El e2e en navegadores reales encontró un defecto del backend que la prueba de humo no podía ver. |

### IA-05: Pruebas (E5)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code (JUnit, Mockito, MockMvc, Vitest y JaCoCo) |
| Prompt relevante | "Construye pruebas unitarias (JUnit + Mockito), de integración (MockMvc) y de frontend (Vitest) que cubran CA-01 a CA-16 y los incidentes; arma la matriz con negativas, regresión, Safari y proveedor caído." |
| Resultado relevante | Backend 55/55 (94,8 % de líneas), frontend 67/67 (92,9 %), matriz de 56 casos (57 después de E6). |
| Decisión del candidato | Pedí hacer los pasos 5 y 6 juntos; se hicieron en ramas y PRs separados. |
| Detectado al validar | 5 defectos **en las pruebas generadas**: helpers que ocultaban los `import static` de MockMvc; una clase `*IT` que Surefire no ejecuta; un cálculo errado de la apertura del circuito; el correlationId no se reenviaba en MockMvc; falta de `cleanup` de Testing Library sin `globals`. Ninguno era un defecto del producto. |
| Decisión | Aceptado con correcciones. |
| Descartado | Testcontainers o Wiremock (sin contratos reales); `sleep` para probar vigencias (se usa `MutableClock`); afirmar que WebKit equivale a Safari 17. |
| Tiempo real / estimado sin IA | ≈ 14 min / 4 h |
| Observaciones de calidad | También las pruebas generadas por IA necesitan revisión; se exigió que cada prueba falle por la razón correcta. |

### IA-06: Pseudocódigo y fragmento representativo (E4)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code |
| Prompt relevante | "Escribe el pseudocódigo del orquestador y elige un fragmento real que concentre las reglas críticas; explica qué asistió la IA y cómo se validó." |
| Resultado relevante | Pseudocódigo de 4 funciones; `updateEmail` + `notifyChange` con 10 decisiones; tabla de asistencia y validación; límites conocidos. |
| Decisión del candidato | Aprobé la elección de `updateEmail` como fragmento. |
| Detectado al validar | Se verificó que las líneas citadas (221–281) correspondan al archivo y que cada decisión ①–⑩ tenga una prueba en E5. |
| Decisión | Aceptado: el fragmento es código real, no ilustrativo. |
| Descartado | `startFlow` (menos reglas críticas); solo pseudocódigo. |
| Tiempo real / estimado sin IA | ≈ 5 min / 45 min |
| Observaciones de calidad | Se declaran los límites del propio fragmento (multi-instancia, bloqueo, outbox). |

### IA-07: Debug de Safari 17 (E6)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + Playwright (WebKit) + Vitest + ESLint |
| Prompt relevante | "Reproduce a propósito el defecto de QA (Safari 17, HTTP 200, *Promise rejected*), plantea hipótesis, aíslalo, corrígelo y deja pruebas y evidencia para el LT." |
| Resultado relevante | Proyecto `safari17` (línea base de Safari 17.0), bisección que aísla `Promise.withResolvers`, fix en dos partes, 3 niveles de regresión y evidencia antes y después. |
| Decisión del candidato | Mi plan pedía **reproducir el bug a propósito**. Aprobé el enfoque de emulación, y que el commit de reproducción quedara marcado como tal. Pedí registrar las revisiones de los PRs. |
| Detectado al validar | (1) **Antes de elegir el defecto** se comprobó empíricamente que Playwright trae WebKit 27.2, que sí soporta `Promise.withResolvers`: sin emular la línea base, la reproducción habría sido falsa. (2) **`eslint-plugin-compat` no detectaba el defecto** (no revisa built-ins de ECMAScript); se agregó `es-x` y se probó que el lint **falle** con la versión defectuosa. (3) Una corrida posterior sobrescribió la captura del defecto; se regeneró restaurando el código defectuoso y verificando con hash que el fix quedara intacto. |
| Decisión | Corregir el **patrón** (todo pasa por `run()`), no solo la API. |
| Descartado | Polyfill (resolvía solo la mitad); "pasa en WebKit = pasa en Safari 17"; un guardarraíl sin probar que detecta el caso. |
| Tiempo real / estimado sin IA | ≈ 14 min / 3 h |
| Observaciones de calidad | Las pruebas Vitest existentes ya detectaban la mitad del defecto, así que el hallazgo de proceso es CI obligatorio (IA-08). La verificación en Safari 17.0–17.3 real sigue pendiente (C-06). |

### IA-08: Implementación y pipeline CI (E3)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + GitHub CLI |
| Prompt relevante | "Documenta E3 e incluye un pipeline de CI que corra mvn test y npm run verify en cada PR." |
| Resultado relevante | 3 jobs verdes al primer intento en Linux (45 s, 24 s y 126 s), configurados como checks obligatorios en modo *strict*; README completo; E3 con 23 tareas y plan de despliegue. |
| Decisión del candidato | Pedí **incluir el pipeline**. Ante el bloqueo de permisos elegí **ajustar la regla** para proteger solo los `.env` con secretos reales (opción 1). |
| Detectado al validar | Se consultaron las versiones vigentes de las Actions en vez de usar las que la IA recordaba. El pipeline se validó **ejecutándolo** antes de documentarlo, y se verificó el comando de arranque del README. La regla `deny Read(./.env.*)`, creada por `ruflo init`, bloqueaba `backend/.env.example`: **no se esquivó**, se consultó primero. |
| Decisión | Aceptado. |
| Descartado | e2e solo en Chromium; escribir el `.env.example` por bash para saltar la regla. |
| Tiempo real / estimado sin IA | ≈ 8 min (+ ≈ 4 min de CI) / 3 h |
| Observaciones de calidad | CI confirmó en Linux lo probado en Windows. |

### IA-09: Consolidación del registro (E7)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + git (timestamps) |
| Prompt relevante | "Consolida E7 con los campos del doc. 15; revisa la atribución de los ajustes." |
| Resultado relevante | Este documento: resumen con totales, atribución separada y tiempos medidos. |
| Decisión del candidato | Pedí revisar la atribución, para no presentar como propios los hallazgos que surgieron al ejecutar. |
| Detectado al validar | Los "tiempos reales" de los registros previos eran **estimaciones infladas** de la IA (p. ej. 50 min para el backend frente a ≈ 23 min según git); se reemplazaron por tiempos medidos. |
| Decisión | Aceptado. |
| Descartado | Mantener los tiempos estimados como si fueran medidos. |
| Tiempo real / estimado sin IA | ≈ 10 min / 45 min |
| Observaciones de calidad | Un registro de IA también puede tener errores de la IA: los datos cuantitativos se contrastan con una fuente objetiva (git, CI). |

---

### IA-10: Entrega final (Word de respuestas, release y zip)

| Campo | Registro |
|-------|----------|
| Herramienta | Claude Code + Microsoft Word (COM) + GitHub CLI |
| Prompt relevante | "Genera el archivo de respuestas como copia nueva del Word de la prueba y haz el release a main con tag." |
| Resultado relevante | `Respuestas_Candidato_DEV.docx` (21 páginas, plantillas 8 y 9 y sección 10 diligenciadas, sección 11 con E1–E7); release `v0.1.0` con Git Flow (PRs #10 a #12, tag y GitHub Release con el jar); zip con el repositorio y su `.git`. |
| Decisión del candidato | Copia nueva del Word (el original intacto); release a `main` con tag. |
| Detectado al validar | (1) Word no abría el archivo: el orden de `shd` y `spacing` en `w:pPr` violaba el esquema; se detectó probando primero que el original sí abría. (2) Al renderizar el PDF: líneas de código partidas y un encabezado de tabla huérfano; se agregó una verificación de ancho que falla el build. (3) El zip de PowerShell 5.1 usaba `\` en las rutas (inválido en macOS/Linux); se rehízo y se probó extrayéndolo con `unzip`. (4) Cambiar a 0.1.0 rompía el nombre del jar en e2e; se fijó `finalName` antes del release. Además, el runner de CI se colgó 20 min instalando navegadores: se canceló, se reintentó y se agregó `timeout-minutes: 5`. |
| Decisión | Aceptado. |
| Descartado | Rehacer el Word desde cero con otra plantilla (se pidió una copia del original); reescribir el historial para el release. |
| Tiempo real / estimado sin IA | ≈ 50 min, de los cuales ≈ 25 fueron espera del CI / 2 h |
| Observaciones de calidad | Cada artefacto se validó con la herramienta del destinatario: Word para el .docx y `unzip` para el zip. |

---

## 5. Lecciones

1. **Generar no es validar.** 27 defectos aparecieron solo al ejecutar: compilación, humo, navegadores, CI. El valor de la IA estuvo en acelerar el ciclo, no en saltárselo.
2. **Verificar las premisas de la IA.** El repositorio "existente", las versiones de las Actions, el soporte de WebKit y los tiempos del propio registro: todo se contrastó con el entorno real.
3. **Las herramientas de seguridad también se prueban.** Un guardarraíl que no falla con el defecto (`eslint-plugin-compat`) da falsa confianza.
4. **Las restricciones no se esquivan.** Ante la regla de permisos, la decisión fue de la persona, no de la IA.
5. **El historial cuenta la historia.** Ramas por paso, commits convencionales con `AI-Assisted:`, PRs con revisión y CI obligatorio permiten auditar qué hizo la IA y qué se validó.
