# E4. Pseudocódigo y fragmento representativo

**HU-CONTACT-001.** Este documento tiene tres partes: (1) el pseudocódigo del flujo completo, (2) el **fragmento real** que concentra las reglas críticas de la HU, tomado del repositorio y ejecutado en las pruebas de E5, y (3) qué parte asistió la IA y cómo se validó.

---

## 1. Pseudocódigo del orquestador

```text
FUNCIÓN iniciarFlujo(tipoDoc, numeroDoc, navegador, ip):
    validar formato (Bean Validation)                         → 400 VALIDATION_ERROR
    ref ← hash(tipoDoc:numeroDoc)                             # nunca el documento en claro
    SI excedeLimite(ref, 5/15 min) O excedeLimite(ip, 30/15 min):
        auditar SECURITY_EVENT;                                → 429 RATE_LIMITED
    afiliado ← conResiliencia(CUSTOMER_DATA, buscarPorDocumento)
    SI afiliado activo:
        reto ← conResiliencia(IDENTITY, generarOtp(afiliado, SMS))   # al celular, no al correo (S-01)
        flujo ← nuevo(OTP_SENT, reto, vence = ahora + 5 min, reenvío = ahora + 30 s)
    SINO:
        flujo ← señuelo(OTP_SENT, sin reto)                   # misma respuesta: anti-enumeración (CA-03)
    auditar FLOW_STARTED, OTP_SENT
    RETORNAR 202 {flowId, otpExpiresAt, resendAvailableAt, mensaje genérico}

FUNCIÓN validarOtp(flowId, código):
    flujo ← buscarVigente(flowId)                             → 404 FLOW_NOT_FOUND
    SI flujo BLOQUEADO                                        → 423 OTP_BLOCKED
    resultado ← (flujo es señuelo) ? INVALID : conResiliencia(IDENTITY, validar(reto, código))
    SEGÚN resultado:
        VALID   → flujo VERIFIED con token (5 min, un solo uso, solo en servidor); auditar
        INVALID → SI intentosRestantes = 0: bloquear + SECURITY_EVENT → 423
                  SINO                                         → 422 OTP_INVALID {remainingAttempts}
        EXPIRED →                                              → 422 OTP_EXPIRED (el front ofrece reenviar)

FUNCIÓN actualizarCorreo(flowId, nuevo, confirmación, idempotencyKey):     ← fragmento §2
    exigir idempotencyKey válida                               → 400
    BAJO bloqueo del flujo:
        SI ya hay resultado para idempotencyKey: RETORNAR ese resultado      # reintento seguro
        SI flujo ≠ VERIFIED O token vencido: SECURITY_EVENT    → 403 IDENTITY_NOT_VERIFIED
        nuevo ← normalizar(nuevo); SI nuevo = actual (sin mayúsculas) → 422
        tx ← conResiliencia(CUSTOMER_DATA, actualizar(afiliado, nuevo, token, idempotencyKey))
        flujo COMPLETED (descarta el token); auditar EMAIL_UPDATED
        estadoNotif ← notificar(nuevo: confirmación, anterior: alerta)      # nunca revierte
        guardar resultado bajo idempotencyKey
        RETORNAR 200 {tx, UPDATED, correo enmascarado, updatedAt ISO-8601, estadoNotif}

FUNCIÓN conResiliencia(proveedor, llamada):
    REPETIR hasta (1 + maxReintentos) veces con backoff exponencial:
        SI circuito(proveedor) abierto → falla rápida 503          # Messaging no tiene circuito
        resultado ← llamada CON timeout(proveedor), propagando correlationId al hilo
        SI éxito: RETORNAR resultado
        SI falla transitoria (timeout, 5xx): registrar intento y reintentar
        SI rechazo de negocio (4xx): propagar SIN reintentar
    → 504 PROVIDER_TIMEOUT | 503 PROVIDER_UNAVAILABLE
```

---

## 2. Fragmento representativo (Java, código real)

`backend/src/main/java/com/colfondos/contactupdate/service/ContactUpdateService.java` (líneas 221–281). Es la parte con más reglas de la HU: el orden de los pasos, la identidad validada, la idempotencia, la llamada resiliente y la notificación que no revierte.

```java
public UpdateEmailResponse updateEmail(String flowId, UpdateEmailRequest request, String idempotencyKey) {
    if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {        // ①
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Falta o es inválido el header Idempotency-Key.");
    }
    ContactFlow flow = requireFlow(flowId);
    synchronized (flow) {                                                                     // ②
        Optional<UpdateEmailResponse> previous = flow.resultFor(idempotencyKey);
        if (previous.isPresent()) {
            return previous.get();                                                            // ③
        }
        if (flow.status() != ContactFlow.Status.VERIFIED || now().isAfter(flow.verifiedUntil())) {
            audit.record(flow, Type.SECURITY_EVENT, Result.FAILURE, null, "IDENTITY_NOT_VERIFIED");
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED,                     // ④
                    "Debes validar tu identidad antes de actualizar el correo.");
        }
        String newEmail = normalizeEmail(request.newEmail());
        if (newEmail.equalsIgnoreCase(flow.currentEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_SAME_AS_CURRENT,
                    "El nuevo correo debe ser distinto al actual.");
        }

        String token = flow.verificationToken();                                              // ⑤
        UpdateResult result = resilient.call(Provider.CUSTOMER_DATA, "updateEmail",
                () -> customerData.updateEmail(flow.customerId(), newEmail, token, idempotencyKey)); // ⑥
        String previousEmail = flow.currentEmail();
        flow.completed(result.transactionId());                                               // ⑦
        audit.record(flow, Type.EMAIL_UPDATED, Result.SUCCESS, Provider.CUSTOMER_DATA, null);

        NotificationStatus notification = notifyChange(flow, newEmail, previousEmail);        // ⑧
        UpdateEmailResponse response = new UpdateEmailResponse(result.transactionId(), "UPDATED",
                Masking.email(newEmail), result.updatedAt(), notification);
        flow.rememberResult(idempotencyKey, response);
        countFlow("success", flow.browser());
        return response;
    }
}

/** La notificación nunca revierte la actualización: si falla queda PENDING para reproceso (D-05). */
private NotificationStatus notifyChange(ContactFlow flow, String newEmail, String previousEmail) {
    Map<String, String> params = Map.of(
            "transactionId", flow.transactionId(),
            "maskedEmail", Masking.email(newEmail));
    boolean sent = send(flow, new EmailNotification(flow.customerId(), newEmail, Template.EMAIL_UPDATED_NEW, params));
    if (previousEmail != null) {
        sent &= send(flow, new EmailNotification(flow.customerId(), previousEmail,               // ⑨
                Template.EMAIL_UPDATED_SECURITY_ALERT, params));
    }
    return sent ? NotificationStatus.SENT : NotificationStatus.PENDING;
}

private boolean send(ContactFlow flow, EmailNotification notification) {
    try {
        resilient.call(Provider.MESSAGING, "sendEmail", () -> messaging.sendEmail(notification));
        audit.record(flow, Type.NOTIFICATION_SENT, Result.SUCCESS, Provider.MESSAGING, notification.template().name());
        return true;
    } catch (ProviderException e) {                                                           // ⑩
        audit.record(flow, Type.NOTIFICATION_PENDING, Result.FAILURE, Provider.MESSAGING,
                notification.template().name() + ":" + e.kind());
        return false;
    }
}
```

### Explicación

| # | Decisión | Por qué | Criterio / fuente |
|---|----------|---------|-------------------|
| ① | La `Idempotency-Key` es obligatoria y tiene formato restringido | Habilita el reintento seguro y evita inyección en logs y almacenes | E2 §3.4 |
| ② | `synchronized (flow)` | Serializa los dobles clics y los reintentos **del mismo flujo**; flujos distintos no se bloquean entre sí | E1 §3.2 (idempotencia) |
| ③ | Si la clave ya se usó, se devuelve el mismo resultado | Un reintento del navegador tras un timeout no actualiza dos veces | CA-12, INC-002 |
| ④ | El orden de los pasos se impone en el servidor y el evento se audita | Nadie puede saltar la validación de identidad llamando directo al API | CA-10, OWASP A01 |
| ⑤ | El token de identidad sale del estado del servidor, nunca del request | El navegador no lo conoce; no se puede robar ni reutilizar | E2 D-04 |
| ⑥ | Llamada con timeout, reintento y circuit breaker | Customer Data puede ser síncrono y lento; el reintento es seguro porque viaja la misma clave | INC-002, E2 §5.3 |
| ⑦ | `completed()` descarta el token | Es de un solo uso | E1 S-04 |
| ⑧ | La notificación va **después** de confirmar la actualización | Messaging no tiene SLA (H-04): su falla no puede deshacer el cambio | D-05, CA-14 |
| ⑨ | Alerta de seguridad al correo **anterior** | Si fue una suplantación, el titular real se entera | E1 S-06 |
| ⑩ | La falla de Messaging se captura, se audita y queda `PENDING` | Trazabilidad para reproceso y KPI de entrega | CA-14, doc. 11 |

### 2.1 Fragmento de la política de resiliencia (`ResilientCaller`, líneas 69–121)

```java
Callable<T> timedAttempt = () -> {
    int n = attempt.incrementAndGet();
    try {
        return policy.timeLimiter().executeFutureSupplier(
                () -> CompletableFuture.supplyAsync(withMdc(mdc, call), executor));   // correlationId al hilo
    } catch (TimeoutException e) {
        throw new ProviderException(provider, ProviderException.Kind.TIMEOUT, ...); // timeout → falla transitoria
    } finally {
        log.atInfo().addKeyValue("provider", provider).addKeyValue("attempt", n)    // log estructurado por intento
                .addKeyValue("outcome", outcome).addKeyValue("durationMs", durationMs).log(...);
    }
};
Callable<T> guarded = policy.circuitBreaker() == null                                 // Messaging: sin circuito
        ? timedAttempt : CircuitBreaker.decorateCallable(policy.circuitBreaker(), timedAttempt);
Callable<T> decorated = Retry.decorateCallable(policy.retry(), guarded);              // solo reintenta ProviderException
```

### 2.2 Contraparte en el frontend (`ContactUpdateWizard.jsx`, línea 23)

```jsx
// Regla de INC-001: toda llamada asíncrona pasa por `run`, que siempre captura el rechazo.
const run = useCallback(async (operation, onSuccess) => {
  dispatch({ type: 'REQUEST' });                      // deshabilita botones: sin doble envío
  try {
    const data = await operation();
    onSuccess(data);
  } catch (error) {
    if (error?.name !== 'ApiError') {
      console.error('[contact-update] error inesperado', { correlationId, error });
    }
    dispatch({ type: 'FAILURE', error: error?.name === 'ApiError' ? error : { code: 'UNEXPECTED' } });
  }
}, [correlationId]);

const confirmUpdate = () =>
  run(() => api.updateEmail(state.flow.flowId, state.email,
                            { correlationId, idempotencyKey: state.idempotencyKey }),  // misma clave en reintentos
      (result) => dispatch({ type: 'UPDATED', result }));
```

### 2.3 Prueba unitaria representativa (`ContactUpdateServiceTest`, línea 268)

```java
@Test
void mismaClaveDeIdempotenciaNoActualizaDosVeces() {
    String flowId = verifiedFlow();

    UpdateEmailResponse first = update(flowId, "nuevo@dominio.com", "key-00000005");
    UpdateEmailResponse retry = update(flowId, "nuevo@dominio.com", "key-00000005");

    assertThat(retry).isSameAs(first);
    verify(customerData, times(1)).updateEmail(any(), any(), any(), any());
}
```

---

## 3. Parte asistida por IA y cómo se validó

| Elemento | Qué generó la IA (Claude Code) | Qué ajusté o decidí yo | Cómo lo validé |
|----------|--------------------------------|------------------------|----------------|
| Pseudocódigo §1 | Primer borrador del flujo a partir de E2 | Agregué el flujo señuelo anti-enumeración, el canal **SMS** (no correo) y la regla de "no reintentar rechazos 4xx" | Revisión contra E1 (CA-01 a CA-16) y E2 §5.3 |
| `updateEmail` + `notifyChange` | Estructura del método y llamadas a los puertos | Decidí: token solo en el servidor (D-04), idempotencia por clave **antes** de validar el estado (③ antes de ④, para que un reintento después de completar no dé un 403 falso), alerta al correo anterior y notificación que no revierte | 19 pruebas unitarias (`ContactUpdateServiceTest`), 12 de integración con MockMvc y prueba de humo 22/22 |
| `ResilientCaller` | Uso de Resilience4j con anotaciones `@Retry` y `@TimeLimiter` | **Descarté las anotaciones**: no permiten propagar el MDC (correlationId) al hilo del time limiter ni elegir qué excepción se reintenta. Pasé a la API programática, y después hice el circuit breaker opcional por proveedor | `ResilientCallerTest` (6), incluida la apertura del circuito y el MDC en el hilo; e2e en 3 navegadores, que detectó el breaker de Messaging que sobraba |
| `run()` del frontend | Wrapper asíncrono con `try/catch` | Agregué el registro de errores no-`ApiError` con correlationId y el mapeo a `UNEXPECTED` | `INC-001: un rechazo inesperado…` (Vitest) y e2e sin errores de consola en WebKit |
| Prueba §2.3 | Caso de idempotencia | Exigí verificar el **número de llamadas** al proveedor, no solo la igualdad de las respuestas | Se ejecuta en `mvn test` (55/55) |

### Revisión crítica: límites conocidos del fragmento

- **`synchronized (flow)` funciona en una sola instancia.** Con varias réplicas, la idempotencia debe apoyarse en un almacén compartido (Redis con `SET NX`, o una restricción única sobre `idempotencyKey` en Customer Data). Queda como pregunta al LT (L-05).
- **El bloqueo se mantiene durante la llamada al proveedor** (≈ 10 s en el peor caso). Solo afecta a solicitudes del **mismo** flujo, lo cual es deseable para evitar dobles envíos, pero el pool de hilos de Tomcat debe dimensionarse con el volumen real (P-07).
- **Las notificaciones son síncronas pero acotadas** (2 s × 3 intentos por mensaje). En producción convendría una cola (*outbox*) para no alargar la respuesta. Depende de si Messaging ofrece cola propia (L-06).
