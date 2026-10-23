package com.colfondos.contactupdate.api;

import java.time.OffsetDateTime;

/** Respuesta al iniciar el flujo o reenviar el OTP. Idéntica exista o no el afiliado (CA-03). */
public record FlowResponse(
        String flowId,
        String otpChannel,
        OffsetDateTime otpExpiresAt,
        OffsetDateTime resendAvailableAt,
        String message) {
}
