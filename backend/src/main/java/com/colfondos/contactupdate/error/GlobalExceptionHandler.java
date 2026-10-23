package com.colfondos.contactupdate.error;

import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.observability.CorrelationIdFilter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Único punto donde una excepción se convierte en respuesta HTTP (E2 §5.1).
 * Nunca expone stack traces, clases internas ni el detalle del proveedor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://colfondos.example/errors/";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        log.atInfo().addKeyValue("errorCode", ex.code()).log("Regla de negocio: {}", ex.getMessage());
        return build(ex.code(), ex.getMessage(), ex.extra());
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ProblemDetail> handleProvider(ProviderException ex) {
        ErrorCode code = ex.kind() == ProviderException.Kind.TIMEOUT
                ? ErrorCode.PROVIDER_TIMEOUT : ErrorCode.PROVIDER_UNAVAILABLE;
        log.atWarn().addKeyValue("errorCode", code).addKeyValue("provider", ex.provider())
                .log("Proveedor externo falló tras reintentos: {}", ex.getMessage());
        return build(code, "El servicio no está disponible en este momento. Intenta de nuevo más tarde.",
                Map.of("provider", ex.provider().name()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> Map.of(
                        "field", e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
                        "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "Revisa los datos ingresados.", Map.of("errors", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
        return build(ErrorCode.VALIDATION_ERROR, "La solicitud no tiene el formato esperado.", Map.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex) {
        return build(ErrorCode.FLOW_NOT_FOUND, "Recurso no encontrado.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR,
                "Ocurrió un error inesperado. Si persiste, comunícate con soporte indicando el código de seguimiento.",
                Map.of());
    }

    private ResponseEntity<ProblemDetail> build(ErrorCode code, String detail, Map<String, Object> extra) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(TYPE_BASE + code.typeSlug()));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        extra.forEach(problem::setProperty);
        return ResponseEntity.status(code.status()).body(problem);
    }
}
