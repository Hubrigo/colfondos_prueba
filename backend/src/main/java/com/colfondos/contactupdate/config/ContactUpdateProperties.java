package com.colfondos.contactupdate.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del orquestador. Se valida al arrancar: si falta una variable
 * obligatoria el servicio no inicia y el mensaje nombra la propiedad (INC-004).
 */
@Validated
@ConfigurationProperties(prefix = "contact-update")
public record ContactUpdateProperties(
        @NotBlank String zoneId,
        @NotEmpty(message = "define CORS_ALLOWED_ORIGINS") List<String> corsAllowedOrigins,
        @NotNull Duration flowTtl,
        @Valid @NotNull Otp otp,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Providers providers) {

    public record Otp(
            @NotNull Duration ttl,
            @Min(1) @Max(5) int maxAttempts,
            @NotNull Duration resendCooldown,
            @Min(0) @Max(5) int maxResends) {
    }

    public record RateLimit(@Min(1) int maxFlowsPerDocument, @Min(1) int maxFlowsPerIp, @NotNull Duration window) {
    }

    public record Providers(
            @Valid @NotNull Provider customerData,
            @Valid @NotNull Provider identity,
            @Valid @NotNull Provider messaging) {
    }

    public record Provider(
            @NotBlank(message = "define la URL del proveedor (PROVIDER_*_URL)") String url,
            @NotNull Duration timeout,
            @Min(0) @Max(5) int maxRetries,
            @NotNull Duration backoff) {
    }
}
