package com.colfondos.contactupdate.service;

import com.colfondos.contactupdate.config.ContactUpdateProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limitador de ventana fija por clave (documento o IP) para el inicio de flujos
 * (anti-abuso, OWASP A04). En producción se apoya en el API gateway o en Redis.
 */
@Component
public class RateLimiter {

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ContactUpdateProperties.RateLimit config;
    private final Clock clock;

    public RateLimiter(ContactUpdateProperties properties, Clock clock) {
        this.config = properties.rateLimit();
        this.clock = clock;
    }

    /** @return {@code true} si los dos límites, por documento y por IP, se respetan */
    public boolean tryAcquire(String customerRef, String clientIp) {
        // Se cuentan ambos siempre: un rechazo por IP también consume cupo del documento.
        boolean documentOk = acquire("doc:" + customerRef) <= config.maxFlowsPerDocument();
        boolean ipOk = acquire("ip:" + clientIp) <= config.maxFlowsPerIp();
        return documentOk && ipOk;
    }

    private int acquire(String key) {
        Instant now = clock.instant();
        return windows.compute(key, (k, w) ->
                w == null || now.isAfter(w.start().plus(config.window()))
                        ? new Window(now, 1)
                        : new Window(w.start(), w.count() + 1)).count();
    }
}
