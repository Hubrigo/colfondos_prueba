package com.colfondos.contactupdate.audit;

import com.colfondos.contactupdate.client.Provider;
import java.time.OffsetDateTime;

/**
 * Registro de trazabilidad transaccional (E2 §7.1). Solo contiene lo que el doc. 08
 * permite: identificadores, resultado, proveedor y fecha/hora. Nunca imágenes o datos
 * faciales, OTP, tokens ni correo en claro.
 */
public record AuditEvent(
        String eventId,
        String flowId,
        String transactionId,
        String correlationId,
        Type type,
        Result result,
        Provider provider,
        String customerRef,
        String browser,
        String detail,
        OffsetDateTime occurredAt) {

    public enum Type {
        FLOW_STARTED,
        OTP_SENT,
        OTP_VERIFIED,
        OTP_FAILED,
        OTP_BLOCKED,
        EMAIL_UPDATED,
        NOTIFICATION_SENT,
        NOTIFICATION_PENDING,
        SECURITY_EVENT
    }

    public enum Result { SUCCESS, FAILURE }
}
