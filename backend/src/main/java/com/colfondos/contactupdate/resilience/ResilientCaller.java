package com.colfondos.contactupdate.resilience;

import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.config.ContactUpdateProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Envuelve cada llamada a un proveedor con timeout, reintento con backoff
 * exponencial y circuit breaker (E2 §5.3; INC-002 e INC-005).
 *
 * <p>Solo se reintentan {@link ProviderException} (fallas transitorias). Los
 * resultados de negocio, como un OTP inválido, nunca llegan aquí como excepción.
 */
@Component
public class ResilientCaller {

    private static final Logger log = LoggerFactory.getLogger(ResilientCaller.class);

    private record Policy(TimeLimiter timeLimiter, Retry retry, CircuitBreaker circuitBreaker) {
    }

    private final Map<Provider, Policy> policies = new EnumMap<>(Provider.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "provider-call");
        t.setDaemon(true);
        return t;
    });
    private final MeterRegistry meterRegistry;

    public ResilientCaller(ContactUpdateProperties properties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        var p = properties.providers();
        policies.put(Provider.CUSTOMER_DATA, policy(Provider.CUSTOMER_DATA, p.customerData()));
        policies.put(Provider.IDENTITY_CAPABILITY, policy(Provider.IDENTITY_CAPABILITY, p.identity()));
        policies.put(Provider.MESSAGING, policy(Provider.MESSAGING, p.messaging()));
    }

    /**
     * Ejecuta {@code call} contra {@code provider} con su política.
     *
     * @throws ProviderException si se agotan los reintentos o el circuito está abierto
     */
    public <T> T call(Provider provider, String operation, Supplier<T> call) {
        Policy policy = policies.get(provider);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        AtomicInteger attempt = new AtomicInteger();

        Callable<T> timedAttempt = () -> {
            int n = attempt.incrementAndGet();
            long start = System.nanoTime();
            String outcome = "error";
            try {
                T result = policy.timeLimiter().executeFutureSupplier(
                        () -> CompletableFuture.supplyAsync(withMdc(mdc, call), executor));
                outcome = "success";
                return result;
            } catch (TimeoutException e) {
                outcome = "timeout";
                throw new ProviderException(provider, ProviderException.Kind.TIMEOUT,
                        operation + " superó " + policy.timeLimiter().getTimeLimiterConfig().getTimeoutDuration());
            } catch (ExecutionException e) {
                throw unwrap(e);
            } finally {
                long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                log.atInfo()
                        .addKeyValue("provider", provider)
                        .addKeyValue("operation", operation)
                        .addKeyValue("attempt", n)
                        .addKeyValue("outcome", outcome)
                        .addKeyValue("durationMs", durationMs)
                        .log("Llamada a proveedor {}.{} intento {}: {}", provider, operation, n, outcome);
                Timer.builder("provider_call_duration")
                        .tag("provider", provider.name())
                        .tag("operation", operation)
                        .tag("outcome", outcome)
                        .register(meterRegistry)
                        .record(Duration.ofMillis(durationMs));
            }
        };

        Callable<T> guarded = policy.circuitBreaker() == null
                ? timedAttempt
                : CircuitBreaker.decorateCallable(policy.circuitBreaker(), timedAttempt);
        Callable<T> decorated = Retry.decorateCallable(policy.retry(), guarded);
        try {
            return decorated.call();
        } catch (CallNotPermittedException e) {
            log.atWarn().addKeyValue("provider", provider).log("Circuito abierto para {}: fallo rápido", provider);
            throw new ProviderException(provider, ProviderException.Kind.UNAVAILABLE, "circuito abierto");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Policy policy(Provider provider, ContactUpdateProperties.Provider config) {
        TimeLimiter timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(config.timeout())
                .cancelRunningFuture(true)
                .build());
        Retry retry = Retry.of(provider.name(), RetryConfig.custom()
                .maxAttempts(config.maxRetries() + 1)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(config.backoff(), 2.0))
                .retryOnException(ProviderException.class::isInstance)
                .failAfterMaxAttempts(false)
                .build());
        if (!config.circuitBreaker()) {
            return new Policy(timeLimiter, retry, null);
        }
        CircuitBreaker circuitBreaker = CircuitBreaker.of(provider.name(), CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(ProviderException.class::isInstance)
                .build());
        return new Policy(timeLimiter, retry, circuitBreaker);
    }

    /** Propaga el correlationId al hilo que ejecuta la llamada. */
    private static <T> Supplier<T> withMdc(Map<String, String> mdc, Supplier<T> call) {
        return () -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return call.get();
            } finally {
                MDC.clear();
            }
        };
    }

    private static RuntimeException unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
