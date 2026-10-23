# E6. Debug Safari 17

> **Reporte de QA (sección 10 de la prueba):** "Safari 17 falla al confirmar actualización. API retorna HTTP 200, pero consola muestra *Promise rejected – Unhandled JavaScript exception*. Chrome y Edge funcionan."

Rama: `bugfix/HU-CONTACT-001-safari17-confirmacion` · Relacionado: INC-001 (doc. 13), CA-15 (E1), C-07 (E5).

---

## 0. Resumen para el LT

| | |
|---|---|
| **Causa raíz** | El código de confirmación usaba `Promise.withResolvers()`, que **Safari solo trae desde la 17.4**. En Safari 17.0–17.3 lanza `TypeError`, y como la cadena `.then()` estaba **fuera** del manejador común `run()` y **sin `catch`**, el rechazo quedaba sin manejo. |
| **Impacto** | El PUT responde 200 y **el correo sí se actualiza**, pero el asistente queda trabado en "Actualizando…": el afiliado no ve el número de transacción ni puede reintentar. Es probable que termine en Contact Center (el KPI que la iniciativa busca bajar). Afecta solo a Safari 17.0–17.3 en macOS e iOS; Chrome, Edge y Safari ≥ 17.4 funcionan, lo que explica por qué "un grupo de usuarios Safari" falla (como en INC-001). |
| **Fix** | (1) `minimumDelay` usa `new Promise`, compatible con todos los navegadores. (2) La confirmación vuelve a pasar por `run()` con `Promise.all`: cualquier rechazo queda manejado y el afiliado ve un mensaje. |
| **Prevención** | (1) Lint `es-x/restrict-to-es2023`, que **falla** ante `Promise.withResolvers`. (2) Proyecto e2e `safari17`, que emula la línea base de Safari 17.0. (3) 3 pruebas Vitest de regresión. (4) Las pruebas Vitest existentes (CA-12, INC-001) **ya** detectaban la mitad del defecto: correr `npm test` en CI lo habría frenado antes de QA. |
| **Datos** | No hay inconsistencia: la actualización y la auditoría quedaron registradas. Si hubo afiliados afectados, la auditoría (`EMAIL_UPDATED` con `browser = Safari 17`) permite identificarlos y confirmarles el cambio. |
| **Pendiente** | Confirmar en **Safari 17.0–17.3 real** (macOS e iOS) antes de cerrar el defecto (E5, C-06). |

---

## 1. Reproducción

### 1.1 Primer intento y por qué no bastaba
Playwright trae **WebKit 27.2**, mucho más nuevo que Safari 17. Antes de reproducir, se comprobó empíricamente qué soporta cada motor:

| Construcción | Chromium 156 | WebKit 27.2 | Safari 17.0 (MDN) |
|--------------|--------------|-------------|-------------------|
| `Promise.withResolvers` | ✅ | ✅ | ❌ (desde 17.4) |
| `Object.groupBy` | ✅ | ✅ | ❌ (desde 17.4) |
| `URL.parse`, `Promise.try`, `RegExp.escape`, *Iterator helpers* | ✅ | ✅ | ❌ (18.x) |
| `Date.parse("2026-10-22 21:04:12")` (formato legado) | ✅ | ✅ | — |

**Conclusión:** correr la suite en el WebKit de Playwright **no reproduce** defectos de compatibilidad con Safari 17. Decir "probado en WebKit" no equivale a "probado en Safari 17".

### 1.2 Emulación de la línea base de Safari 17.0
`frontend/e2e/safari17-baseline.js` define el proyecto **`safari17`**: WebKit con el User-Agent de Safari 17 y un *init script* que **elimina, antes de cargar la app**, las APIs que Safari agregó después de la 17.0. La lista y las versiones vienen de MDN y browser-compat-data.

> Limitación: emula la **ausencia de APIs**, no las diferencias de motor (parser, CSS, layout). Es una herramienta de reproducción y de regresión, **no** una certificación.

### 1.3 Pasos
1. Línea base en `develop` (commit `ace20a8`): **24/24** en chromium, edge, webkit y safari17. El código liberable no tiene el defecto.
2. Se **reintroduce a propósito** el estado del release candidate que probó QA (commit `0fc01a2`, marcado como reproducción). Es una "mejora de UX" que mantiene el indicador "Actualizando…" un mínimo de 600 ms:

```js
// utils/timing.js (versión defectuosa)
export function minimumDelay(startedAt, minMs = 600) {
  const { promise, resolve } = Promise.withResolvers();          // ← Safari ≥ 17.4
  setTimeout(resolve, Math.max(0, minMs - (Date.now() - startedAt)));
  return promise;
}

// ContactUpdateWizard.jsx (versión defectuosa)
const confirmUpdate = () => {
  const startedAt = Date.now();
  dispatch({ type: 'REQUEST' });
  api.updateEmail(/* … */)
    .then((result) => minimumDelay(startedAt).then(() => dispatch({ type: 'UPDATED', result })));
};                                                                // ← fuera de run(), sin catch
```

3. `npx playwright test e2e/safari17.spec.js`. La prueba verifica que el PUT devuelva 200 y que no haya errores en consola:

| Proyecto | Resultado | Observado |
|----------|-----------|-----------|
| chromium | ✅ | Flujo completo |
| edge | ✅ | Flujo completo |
| webkit (27.2) | ✅ | Flujo completo: **el motor moderno oculta el defecto** |
| **safari17** | ❌ | `PUT /email → 200`; `TypeError: Promise.withResolvers is not a function`; `[contact-update] promesa rechazada sin manejo`; la pantalla queda en "Actualizando…" |

Evidencia: `docs/evidencias/e6-1-reproduccion.txt`, `docs/evidencias/e6-safari17-defecto-trabado.png`.

---

## 2. Hipótesis

| # | Hipótesis | Cómo se descarta o confirma | Resultado |
|---|-----------|-----------------------------|-----------|
| H1 | El backend responde distinto a Safari (CORS, formato, cabeceras) | El PUT devuelve **200** con el mismo cuerpo en los 4 proyectos; el backend no tiene lógica por navegador | ❌ Descartada |
| H2 | El cuerpo de la respuesta trae algo que Safari no puede procesar (por ejemplo, una fecha en formato no ISO) | `updatedAt` es ISO-8601 (contrato de E2); `formatDateTime` no lanza ante fechas inválidas (prueba C-04); WebKit 27 parsea el formato legado | ❌ Descartada |
| H3 | Se usa una **API de JavaScript que Safari 17 no tiene** | El error dice literalmente `Promise.withResolvers is not a function` | ✅ Confirmada en §3 |
| H4 | La promesa no está manejada: por eso la UI no se recupera | La cadena `.then()` de `confirmUpdate` no pasa por `run()` y no tiene `catch` | ✅ Confirmada en §3 |

---

## 3. Aislamiento

### 3.1 Bisección sobre la línea base (`e2e/safari17-aislamiento.spec.js`, WebKit)

| Escenario | Resultado |
|-----------|-----------|
| Se elimina **solo** `Promise.withResolvers` | ❌ falla |
| Se eliminan **todas las demás** APIs de la lista y se conserva `Promise.withResolvers` | ✅ pasa |

La causa es esa única API. Evidencia: `docs/evidencias/e6-2-aislamiento.txt`. El servidor de Vite también registra el rechazo: `[Unhandled rejection] TypeError: Promise.withResolvers is not a function`.

### 3.2 Nivel unitario (Vitest, sin navegador)
Se escribieron las pruebas de regresión **antes** del fix (`timing.test.js` y 2 casos E6 en `ContactUpdateWizard.test.jsx`), eliminando `Promise.withResolvers` en jsdom. Resultado con el código defectuoso: **5 fallas** (`docs/evidencias/e6-3-vitest-rojo.txt`):

- `minimumDelay › funciona sin Promise.withResolvers`: `TypeError` (causa H3).
- `E6: en Safari 17.0 … confirmar llega al resultado`: no llega al resultado (impacto).
- `E6: si la actualización falla … Confirmar vuelve a habilitarse`: el botón queda deshabilitado (causa H4).
- **`CA-12` e `INC-001`, que ya existían**: fallan porque la confirmación dejó de pasar por `run()`. **El defecto habría sido detectado por la suite existente si `npm test` corriera en CI** (lección para E3).

---

## 4. Fix

Commit `fix(frontend)` sobre la rama:

```js
// utils/timing.js
export function minimumDelay(startedAt, minMs = 600) {
  return new Promise((resolve) => {                                 // compatible con todos
    setTimeout(resolve, Math.max(0, minMs - (Date.now() - startedAt)));
  });
}

// ContactUpdateWizard.jsx
const confirmUpdate = () => {
  const startedAt = Date.now();
  return run(                                                       // manejador común: siempre captura
    async () => {
      const [result] = await Promise.all([
        api.updateEmail(state.flow.flowId, state.email, { correlationId, idempotencyKey: state.idempotencyKey }),
        minimumDelay(startedAt),
      ]);
      return result;
    },
    (result) => dispatch({ type: 'UPDATED', result }),
  );
};
```

**Por qué no un polyfill:** el polyfill de `Promise.withResolvers` arreglaría H3, pero no H4. Cualquier otra excepción en esa cadena volvería a dejar la UI trabada. Corregir el patrón (todo pasa por `run()`) elimina la **clase** de defecto, no solo esta instancia. Tampoco hace falta la API: `new Promise` resuelve lo mismo.

**Recuperación del afiliado:** si la respuesta se perdiera, el reintento usa la **misma** `Idempotency-Key`, así que el backend devuelve la misma transacción sin actualizar dos veces (CA-12).

---

## 5. Pruebas y prevención

| Control | Tipo | Detecta | Resultado tras el fix |
|---------|------|---------|-----------------------|
| `e2e/safari17.spec.js` en 4 proyectos | E2E (regresión) | La UI llega al resultado sin errores JS con el API en 200 | ✅ 4/4 (`e6-5-fix-navegadores.txt`) |
| `e2e/safari17-aislamiento.spec.js` | E2E (bisección) | Qué API ausente causa la falla | ✅ 2/2 |
| Suite e2e completa en `safari17` | E2E | Todos los escenarios con la línea base de Safari 17.0 | ✅ 26/26 en total (`frontend-e2e-navegadores.txt`) |
| `timing.test.js` (3) | Vitest | `minimumDelay` sin `Promise.withResolvers` y sus tiempos | ✅ |
| `ContactUpdateWizard.test.jsx` (2 casos E6) | Vitest | Flujo sin la API y recuperación ante error | ✅ 72/72 en total (`e6-4-vitest-verde.txt`) |
| **ESLint `es-x/restrict-to-es2023`** | Estático | Built-ins de ES2024 o posteriores **al escribir el código** | ✅ el código actual pasa; la versión defectuosa **falla** (`e6-6-lint-detecta-defecto.txt`) |
| `npm run verify` (lint + test + build) | Pipeline local | Todo lo anterior antes del PR | ✅ |

**Hallazgo sobre las herramientas:** `eslint-plugin-compat`, la opción habitual, **no detectó** el defecto: valida APIs web contra browser-compat-data, pero no los built-ins de ECMAScript. Se mantiene para las APIs web, y para ECMAScript se agregó `eslint-plugin-es-x`. Se verificó que cada guardarraíl **falle** con el código defectuoso, no solo que pase con el corregido.

---

## 6. Evidencia para el LT

| Archivo (`docs/evidencias/`) | Contenido |
|------------------------------|-----------|
| `e6-1-reproduccion.txt` | Playwright: chromium, edge y webkit ✅ · safari17 ❌ con el `TypeError` y el rechazo sin manejo |
| `e6-safari17-defecto-trabado.png` | **Antes:** pantalla trabada en "Actualizando…" aunque el correo ya se actualizó |
| `e6-safari17-fix-resultado.png` | **Después:** mismo escenario en `safari17` con el fix; llega al resultado con el número de transacción |
| `e6-2-aislamiento.txt` | Bisección: solo `Promise.withResolvers` reproduce |
| `e6-3-vitest-rojo.txt` | 5 pruebas en rojo con el defecto (incluye CA-12 e INC-001) |
| `e6-4-vitest-verde.txt` | 72/72 tras el fix |
| `e6-5-fix-navegadores.txt` | Prueba E6 ✅ en los 4 proyectos |
| `e6-6-lint-detecta-defecto.txt` | `es-x/no-promise-withresolvers` sobre la versión defectuosa |
| Historial de la rama | `ace20a8` (línea base) → `0fc01a2` (reproducción) → pruebas en rojo → fix → guardarraíl |

**Checklist de cierre del defecto (para QA y el LT):**
- [x] Causa raíz identificada y aislada
- [x] Fix con pruebas de regresión en 3 niveles (unitario, e2e y estático)
- [x] Sin efecto en Chrome, Edge ni en Safari moderno (suite completa en verde)
- [ ] Verificación en **Safari 17.0–17.3 real**, macOS e iOS (QA, C-06)
- [ ] Consultar en la auditoría las actualizaciones con `browser = Safari 17` durante la ventana afectada y confirmarles el cambio a esos afiliados
- [ ] Agregar `npm run verify` al pipeline de CI (E3)
