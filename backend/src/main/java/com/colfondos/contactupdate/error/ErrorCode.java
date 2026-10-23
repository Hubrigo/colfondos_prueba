package com.colfondos.contactupdate.error;

import org.springframework.http.HttpStatus;

/** Catálogo de errores estable (E2 §5.2). El frontend traduce {@code code} a mensajes. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Datos inválidos"),
    FLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Proceso no encontrado o vencido"),
    OTP_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Código incorrecto"),
    OTP_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Código expirado"),
    OTP_BLOCKED(HttpStatus.LOCKED, "Proceso bloqueado por seguridad"),
    OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Reenvío no disponible todavía"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes"),
    IDENTITY_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Identidad no validada"),
    EMAIL_SAME_AS_CURRENT(HttpStatus.UNPROCESSABLE_ENTITY, "El correo es igual al actual"),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado"),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Sufijo del tipo de problema (RFC 7807), p. ej. {@code otp-invalid}. */
    public String typeSlug() {
        return name().toLowerCase().replace('_', '-');
    }
}
