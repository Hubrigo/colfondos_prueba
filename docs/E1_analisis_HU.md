# E1. Análisis de la Historia de Usuario

**HU-CONTACT-001.** *Como afiliado quiero actualizar mi correo electrónico para recibir comunicaciones en mi nueva dirección.*
**Criterios iniciales:** validar identidad, validar correo, confirmar actualización, notificar y registrar trazabilidad.

Convención usada en todo el documento:

| Etiqueta | Significado |
|----------|-------------|
| **[H-xx]** | **Hecho**: está escrito en el Mini-RAG; se cita el documento. |
| **[S-xx]** | **Supuesto**: decisión mía para poder avanzar; requiere validación del PO o el LT. |
| **[R-xx]** | **Recomendación**: propuesta técnica derivada de hechos y supuestos. |

---

## 1. Entendimiento

### 1.1 Problema real detrás de la HU
El afiliado no logra completar procesos digitales porque **el mecanismo de verificación depende de datos de contacto que pueden estar desactualizados** (doc. 01, caso de negocio §3). La HU no es un simple "editar un campo": el afiliado llega a este flujo justamente porque su canal registrado puede no servirle.

> **Tensión central del caso:** si el OTP se envía al correo actual y ese correo es el que está desactualizado, el flujo se bloquea a sí mismo. El diseño debe resolver por qué canal se valida la identidad (ver S-01 y P-01).

### 1.2 Actores y sistemas

| Elemento | Rol en la HU | Fuente |
|----------|--------------|--------|
| Afiliado | Inicia el flujo desde el portal (módulo *Actualización de Datos*) | doc. 02 |
| Portal PortalX | Canal web; el módulo existe en 7.0 y el roadmap pide lo nuevo en 7.4 | doc. 02, 03 |
| Customer Data API / Service | Consulta el perfil y **actualiza el dato de contacto** | doc. 06, 07 |
| Identity Capability API / Verification Service | **Genera y valida el código de verificación** y registra el resultado | doc. 06, 07 |
| Identity Orchestration Service | Autenticación, validación de identidad y tokens | doc. 07 |
| Messaging API / Service | Notificación transaccional por correo o SMS | doc. 06, 07 |
| IntegrationHub OnPrem / Cloud | Capa de integración gobernada | doc. 04, 05 |

### 1.3 Flujo propuesto (alto nivel)
1. **Identificación:** tipo y número de documento → consulta del afiliado (Customer Data).
2. **Validación de identidad:** OTP generado por Identity Capability y entregado al canal registrado (S-01) → validación del código.
3. **Captura del nuevo correo:** con doble digitación y validación de formato, longitud y que sea distinto al actual.
4. **Confirmación:** resumen con el correo enmascarado y botón *Confirmar*.
5. **Actualización:** Customer Data API, con el token de validación de identidad del paso 2.
6. **Notificación:** al correo **nuevo** (confirmación) y al **anterior** (aviso de seguridad), vía Messaging.
7. **Resultado y trazabilidad:** pantalla final con el número de transacción y un registro de auditoría sin biometría.

---

## 2. Hechos vs. supuestos

### 2.1 Hechos extraídos del Mini-RAG

| ID | Hecho | Doc. |
|----|-------|------|
| H-01 | La actualización de datos de contacto **requiere validación previa de identidad**; el catálogo **no describe el flujo exacto**. | 06 |
| H-02 | Identity Capability API genera y valida códigos de verificación y registra el resultado. **No hay contrato de validación facial.** | 06 |
| H-03 | El **proveedor definitivo de validación facial no está definido**, al igual que las reglas de excepción y reintentos y el flujo para validaciones fallidas. | 01 |
| H-04 | Messaging API soporta correo y SMS; **no documenta tiempos de entrega ni política de reintentos**. | 06 |
| H-05 | El portal vigente opera en **PortalX 7.0** (doc. de 2025-11); el roadmap (2026-05) indica que **lo nuevo debe construirse en 7.4 cuando sea viable**. | 02, 03 |
| H-06 | Las integraciones productivas están en **IntegrationHub OnPrem**; el roadmap empuja a **Cloud**, pero permite **permanecer en OnPrem** si hay dependencias con sistemas internos o flujos productivos. | 04, 05 |
| H-07 | Lineamientos backend: logging estructurado, Correlation ID, manejo centralizado de errores, pruebas unitarias, documentación de endpoints y **variables de ambiente documentadas**. | 07 |
| H-08 | **No se almacenan fotos, videos ni imágenes faciales.** Para trazabilidad se permite: id de transacción, resultado, proveedor y fecha/hora. | 08 |
| H-09 | Controles mínimos: TLS ≥ 1.2, gestión segura de secretos, validación de entradas, OWASP Top 10 y registro de eventos de seguridad. Escaneo previo a producción: **3 días hábiles**. | 08 |
| H-10 | QA exige pruebas funcionales, negativas, de integración, de regresión, **de compatibilidad (Chrome, Edge, Safari)** y **ante indisponibilidad de servicios externos**. | 09 |
| H-11 | Flujo completo **< 3 min**; validación de identidad **< 60 s** en condiciones normales; el afiliado **debe recibir confirmación**. | 14 |
| H-12 | Incidentes previos: **INC-001** JS no soportado en Safari; **INC-002** timeout con proveedor síncrono; **INC-003** OTP que expira antes de terminar; **INC-004** variable de ambiente faltante; **INC-005** degradación del proveedor de identidad. | 13 |
| H-13 | Despliegue: comité los martes a las 11:00 AM; ventana después de las 9:00 PM; cierre con evidencias en Mesa de Servicio. | 12 |
| H-14 | Observabilidad: Correlation ID por transacción, logs estructurados, trazabilidad de llamadas API e **indicadores por navegador**. | 11 |
| H-15 | El alcance de la iniciativa incluye **correo y celular**; la **HU evaluada cubre solo el correo**. | 01, Word §3–4 |

### 2.2 Inconsistencias y vacíos detectados

| # | Hallazgo | Impacto en la HU |
|---|----------|------------------|
| I-01 | **Vigencia de documentos:** la arquitectura del portal (7.0, 2025-11) es anterior al roadmap (7.4, 2026-05). Ninguno dice si el módulo *Actualización de Datos* ya migró. | Define dónde se construye el componente frontend y qué librerías JS existen (riesgo de INC-001). |
| I-02 | **OnPrem vs. Cloud:** las APIs del catálogo (doc. 06, 2025-11) no indican en qué plataforma están expuestas. | Cambia latencias, timeouts y quién opera el reintento. |
| I-03 | **Validación de identidad sin flujo definido** (H-01, H-02, H-03). | Bloqueante para el criterio "validar identidad". Se resuelve con un supuesto (S-01). |
| I-04 | **Alcance correo + celular vs. HU solo correo** (H-15). | Riesgo de sobredimensionar; se limita a correo y se deja el diseño extensible. |
| I-05 | **Messaging sin SLA ni reintentos** (H-04). | La notificación no puede bloquear la actualización (S-05). |
| I-06 | **Duración del OTP no documentada**, pese a que INC-003 se resolvió "ajustando la expiración". | Hay que fijar un valor y validarlo (S-03). |
| I-07 | **Tiempo de la prueba:** el Word indica 4–5 h y el correo, 3 h. | Prioricé los entregables con más peso (E2, E4, E5 y E7) y el código compacto. |

### 2.3 Supuestos de trabajo

| ID | Supuesto | Justificación | Valida |
|----|----------|---------------|--------|
| S-01 | La identidad se valida con **OTP enviado al celular registrado (SMS)** mediante Identity Capability API, porque el correo es justamente el dato que se va a cambiar. La validación facial queda como **extensión** cuando exista proveedor. | H-01, H-02, H-03 y la tensión de §1.1 | PO / LT |
| S-02 | La identificación inicial usa **tipo y número de documento**. El sistema responde igual exista o no el afiliado, para evitar enumeración de usuarios. | OWASP (H-09) | LT / Ciberseguridad |
| S-03 | OTP de **6 dígitos**, **vigencia de 5 min**, **máximo 3 intentos** y **reenvío permitido a los 30 s** (máximo 3 reenvíos). Con 5 min de vigencia, el OTP cubre de sobra el paso de validación, que debe durar menos de 60 s. | INC-003, H-11 | PO / Ciberseguridad |
| S-04 | Al validar el OTP, Identity entrega un **token de verificación de corta vida (5 min)**, de un solo uso, ligado al afiliado. Customer Data lo exige para actualizar. | H-01 | LT / Integración |
| S-05 | La notificación es **asíncrona respecto al resultado**: si Messaging falla tras los reintentos, la actualización **se mantiene** y la falla se registra para reproceso. | H-04, H-11 | PO / LT |
| S-06 | Se notifica al **correo nuevo** (confirmación) y al **correo anterior** (alerta de seguridad ante posible suplantación). | Buena práctica antifraude | PO / Ciberseguridad |
| S-07 | Las APIs se consumen por **IntegrationHub OnPrem** (productivo hoy) a través de un servicio orquestador Java. La migración a Cloud queda fuera del alcance de la HU. | H-06 | LT / Arquitectura |
| S-08 | El componente frontend se construye **desacoplado** (React), pensado para integrarse en **PortalX 7.4**, sin depender de librerías legadas de 7.0. | H-05, doc. 03 | LT / Arquitectura Canales |
| S-09 | Para la prueba, las tres APIs se **simulan** (mocks configurables con latencia y fallas) con **datos sintéticos**. | Sin contratos ni ambientes disponibles | — |

---

## 3. Validaciones

### 3.1 Frontend (experiencia y prevención)

| Campo o paso | Validación |
|--------------|------------|
| Tipo de documento | Obligatorio; lista cerrada (CC, CE, PA, TI). |
| Número de documento | Obligatorio; solo dígitos (o alfanumérico si es PA); longitud de 5 a 15. |
| OTP | 6 dígitos numéricos; `inputmode="numeric"` y `autocomplete="one-time-code"`; contador visible de vigencia; aviso **antes** de expirar (INC-003). |
| Nuevo correo | Obligatorio; formato RFC 5322 simplificado; máximo 254 caracteres; normalizado con trim y dominio en minúsculas. |
| Confirmación de correo | Debe coincidir con el nuevo correo; pegar en este campo se desaconseja, sin bloquearlo. |
| Correo distinto al actual | Se valida en el backend, porque el frontend no conoce el correo actual en claro. |
| Envío | Botones deshabilitados mientras hay una solicitud en curso (evita doble envío); **toda promesa con manejo de rechazo** (INC-001). |

### 3.2 Backend (autoridad final)

| Regla | Detalle |
|-------|---------|
| Validación de entradas | Bean Validation en los DTO; se rechazan campos desconocidos; respuesta `400` con códigos de error estables. |
| Estado del flujo | El orquestador exige el orden **identificado → OTP validado → actualización**. No se puede actualizar sin un token de verificación vigente. |
| Token de verificación | Vigente, de un solo uso, del mismo afiliado y del mismo flujo (`flowId`). |
| Correo | Formato válido, distinto al actual y no en lista de dominios bloqueados (configurable). |
| OTP | Intentos limitados (3) → bloqueo temporal del flujo; vigencia (S-03). |
| Rate limiting | Por documento y por IP para generar el OTP (anti-abuso). |
| Idempotencia | `Idempotency-Key` en la confirmación para evitar dobles actualizaciones por reintento del cliente. |
| Datos sensibles | Correos y documento **enmascarados** en logs; sin biometría (H-08). |

---

## 4. Dudas para el PO y el LT

### Para el PO

| # | Pregunta | Por qué importa |
|---|----------|-----------------|
| P-01 | ¿Por qué canal se valida la identidad si el correo registrado está desactualizado? ¿Se confirma el **SMS al celular registrado** (S-01)? ¿Qué pasa si **ambos** datos están desactualizados? | Define el flujo principal y el alterno; el doc. 01 lo deja pendiente. |
| P-02 | ¿Esta HU cubre **solo el correo**, o también el celular como dice la iniciativa? | Alcance y estimación (I-04). |
| P-03 | ¿Hay que **verificar la titularidad del nuevo correo** (código o enlace al correo nuevo) antes de dejarlo activo? | Calidad del dato frente al tiempo total del flujo (< 3 min). |
| P-04 | ¿Cuál es el flujo de atención cuando la validación falla o se agotan los intentos? ¿Se deriva a oficina o a Contact Center? | Mensajes y estados finales (doc. 01, "pendiente"). |
| P-05 | ¿Se debe avisar al **correo anterior** del cambio (S-06)? | Antifraude y textos de notificación. |
| P-06 | ¿Cuántas actualizaciones por afiliado se permiten en un periodo (por ejemplo, 1 cada 24 h)? | Reglas antifraude. |
| P-07 | ¿Volumen transaccional estimado? | Dimensionamiento y rate limits (doc. 01, "pendiente"). |

### Para el LT

| # | Pregunta | Por qué importa |
|---|----------|-----------------|
| L-01 | ¿El módulo *Actualización de Datos* se construye en **PortalX 7.4** o se modifica en **7.0**? ¿Qué dependencias 7.0 existen? | Stack frontend, librerías JS, regresión y riesgo de Safari (I-01). |
| L-02 | ¿Customer Data, Identity y Messaging están expuestas en **IntegrationHub OnPrem o Cloud**? ¿Cuáles son los **timeouts de la capa** expuesta al portal? | Configuración de timeouts y reintentos (INC-002). |
| L-03 | ¿Cuál es el **contrato real** del token de validación de identidad que exige Customer Data (formato, vigencia, quién lo emite)? | El catálogo no lo describe (H-01). |
| L-04 | ¿Reutilizo **Identity Orchestration Service** y **Verification Service** existentes, o el orquestador nuevo llama directo a las APIs? | Evita duplicar responsabilidades (doc. 07). |
| L-05 | ¿Existe un **almacén de auditoría** corporativo o se define uno nuevo para la trazabilidad? | Persistencia del registro (H-08). |
| L-06 | ¿Messaging ofrece **cola o reintento** propio, o el orquestador debe implementarlo? | Política de reintentos (H-04). |
| L-07 | ¿El servicio debe pasar el **escaneo de ciberseguridad** (3 días hábiles) antes del comité de cambios? | Planeación del despliegue (H-09, H-13). |

---

## 5. Criterios de aceptación refinados (Gherkin)

```gherkin
Feature: Actualización de correo electrónico del afiliado (HU-CONTACT-001)

  Background:
    Given un afiliado activo con documento "CC 1012345678"
    And celular registrado "300****567" y correo registrado "an***@correo.com"

  # --- Camino feliz ---
  Scenario: CA-01 Actualización exitosa del correo
    Given el afiliado se identifica con "CC" y "1012345678"
    When solicita el código de verificación
    Then recibe un OTP de 6 dígitos en el celular registrado
    When ingresa el OTP correcto dentro de su vigencia
    And ingresa y confirma el correo "nuevo.correo@dominio.com"
    And confirma la actualización
    Then el sistema actualiza el correo en Customer Data
    And muestra el resultado exitoso con el número de transacción
    And envía la confirmación al correo nuevo y un aviso al correo anterior
    And registra la trazabilidad con id de transacción, resultado, proveedor y fecha/hora
    And el flujo completo tarda menos de 3 minutos

  # --- Identificación ---
  Scenario: CA-02 Documento con formato inválido
    When el afiliado ingresa el número de documento "12AB"
    Then el sistema muestra "Ingresa solo números, entre 5 y 15 dígitos"
    And no consulta Customer Data

  Scenario: CA-03 Afiliado inexistente sin enumeración
    When el afiliado se identifica con un documento no registrado
    Then el sistema responde con el mismo mensaje genérico que para uno registrado
    And no revela si el documento existe

  # --- Validación de identidad (OTP) ---
  Scenario: CA-04 OTP incorrecto con intentos restantes
    Given el afiliado tiene un OTP vigente
    When ingresa un OTP incorrecto
    Then el sistema muestra "Código incorrecto. Te quedan 2 intentos"

  Scenario: CA-05 OTP bloqueado por intentos agotados
    Given el afiliado ingresó 3 OTP incorrectos
    Then el flujo se bloquea temporalmente
    And se le indica el canal alterno de atención

  Scenario: CA-06 OTP expirado (INC-003)
    Given el OTP fue generado hace más de 5 minutos
    When el afiliado lo ingresa
    Then el sistema muestra "Tu código expiró. Solicita uno nuevo"
    And ofrece reenviar el código sin perder los datos ya ingresados

  Scenario: CA-07 Aviso preventivo de expiración
    Given quedan 30 segundos de vigencia del OTP
    Then el asistente muestra un aviso con el contador y la opción de reenviar

  # --- Validación de correo ---
  Scenario Outline: CA-08 Correo inválido
    When el afiliado ingresa el correo "<correo>"
    Then el sistema muestra "<mensaje>"
    Examples:
      | correo                  | mensaje                                       |
      | sin-arroba.com          | Ingresa un correo válido                      |
      | a@b                     | Ingresa un correo válido                      |
      | an***@correo.com actual | El nuevo correo debe ser distinto al actual   |

  Scenario: CA-09 Correos que no coinciden
    When el afiliado ingresa "a@dominio.com" y confirma "b@dominio.com"
    Then el sistema muestra "Los correos no coinciden"

  # --- Seguridad del flujo ---
  Scenario: CA-10 Actualización sin validación de identidad
    When se invoca la actualización sin un token de verificación vigente
    Then el backend responde 401/403
    And no actualiza el correo
    And registra el evento de seguridad

  Scenario: CA-11 Sin almacenamiento de biometría
    Then ningún log ni registro de auditoría contiene imágenes, video o plantillas faciales
    And el correo y el documento aparecen enmascarados en los logs

  # --- Proveedores externos ---
  Scenario: CA-12 Timeout transitorio del proveedor (INC-002)
    Given Customer Data responde con timeout en el primer intento
    When el sistema reintenta con backoff y el segundo intento responde OK
    Then el afiliado ve el resultado exitoso
    And el log registra el reintento con el mismo correlationId

  Scenario: CA-13 Proveedor de identidad no disponible (INC-005)
    Given Identity Capability no responde tras los reintentos
    Then el afiliado ve "No pudimos validar tu identidad en este momento. Intenta más tarde"
    And el error se registra con correlationId y proveedor

  Scenario: CA-14 Messaging falla después de actualizar
    Given el correo se actualizó correctamente
    And Messaging falla tras los reintentos
    Then el afiliado ve el resultado exitoso
    And la notificación queda registrada como pendiente de reproceso

  # --- Compatibilidad (INC-001) ---
  Scenario: CA-15 Confirmación en Safari 17
    Given el afiliado usa Safari 17
    When confirma la actualización y la API responde 200
    Then el asistente muestra el resultado sin errores JavaScript en consola
    And el mismo escenario pasa en Chrome y Edge

  # --- Observabilidad ---
  Scenario: CA-16 Correlación de la transacción
    Then cada petición del flujo lleva el header X-Correlation-Id
    And el mismo valor aparece en los logs del frontend, el backend y las llamadas a APIs
```

---

## 6. Riesgos priorizados

| Riesgo | Prob. | Impacto | Mitigación |
|--------|-------|---------|------------|
| Canal de validación también desactualizado (P-01) | Alta | Alto | Flujo alterno definido con el PO; diseño extensible a validación facial. |
| Incompatibilidad en Safari (INC-001) | Media | Alto | Sin APIs JS no soportadas, `catch` en toda promesa, prueba en Safari y polyfills puntuales. |
| Timeout o degradación de proveedor (INC-002, INC-005) | Media | Alto | Timeouts explícitos, reintentos con backoff solo para fallas transitorias y mensajes claros. |
| OTP expira antes de terminar (INC-003) | Media | Medio | Vigencia de 5 min, contador, aviso previo y reenvío sin perder el estado. |
| Variable de ambiente faltante (INC-004) | Media | Alto | `.env.example`, validación al arrancar (fail-fast) y checklist del PR. |
| Escaneo de ciberseguridad (3 días) no planeado | Media | Medio | Incluirlo en el plan de despliegue antes del comité. |

---

## 7. Fuera de alcance de esta HU
- Actualización del número celular (se deja el diseño extensible: `contactType`).
- Validación facial (no hay proveedor ni contrato; H-02, H-03).
- Migración de integraciones a IntegrationHub Cloud.
