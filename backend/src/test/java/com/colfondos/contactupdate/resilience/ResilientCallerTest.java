package com.colfondos.contactupdate.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.error.BusinessException;
import com.colfondos.contactupdate.error.ErrorCode;
import com.colfondos.contactupdate.observability.CorrelationIdFilter;
import com.colfondos.contactupdate.support.TestFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Política de resiliencia real (Resilience4j) con timeouts de 200 ms: INC-002 e INC-005. */
class ResilientCallerTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ResilientCaller caller = new ResilientCaller(TestFixtures.properties(), meters);

    @AfterEach
    void tearDown() {
        caller.shutdown();
        MDC.clear();
    }

    @Test
    @DisplayName("CA-12: una falla transitoria se reintenta y la llamada termina bien")
    void reintentaFallaTransitoria() {
        AtomicInteger attempts = new AtomicInteger();

        String result = caller.call(Provider.CUSTOMER_DATA, "updateEmail", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw transientFailure(Provider.CUSTOMER_DATA);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("INC-002: superar el timeout cuenta como falla y se agotan los reintentos")
    void timeoutAgotaReintentos() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> caller.call(Provider.CUSTOMER_DATA, "findByDocument", () -> {
            attempts.incrementAndGet();
            sleep(1_000);
            return "tarde";
        }))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.kind()).isEqualTo(ProviderException.Kind.TIMEOUT));
        assertThat(attempts).hasValue(3); // 1 intento + 2 reintentos (max-retries: 2)
        assertThat(meters.find("provider_call_duration").tag("outcome", "timeout").timer().count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un rechazo de negocio nunca se reintenta")
    void errorDeNegocioNoSeReintenta() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> caller.call(Provider.CUSTOMER_DATA, "updateEmail", () -> {
            attempts.incrementAndGet();
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED, "403");
        })).isInstanceOf(BusinessException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("INC-005: con el proveedor degradado el circuito se abre y falla rápido")
    void circuitoSeAbreAnteDegradacion() {
        AtomicInteger attempts = new AtomicInteger();
        // 3 llamadas × 3 intentos = 9 fallos; el circuito exige un mínimo de 10 para evaluar.
        for (int i = 0; i < 3; i++) {
            callFailing(Provider.IDENTITY_CAPABILITY, attempts);
        }
        // El 10.º fallo (1.er intento de la 4.ª llamada) abre el circuito; el reintento ya no sale.
        assertThatThrownBy(() -> callFailing(Provider.IDENTITY_CAPABILITY, attempts))
                .hasMessageContaining("circuito abierto");
        assertThat(attempts).hasValue(10);

        assertThatThrownBy(() -> callFailing(Provider.IDENTITY_CAPABILITY, attempts))
                .hasMessageContaining("circuito abierto");
        assertThat(attempts).as("con el circuito abierto no se llama al proveedor").hasValue(10);
    }

    @Test
    @DisplayName("E2 §5.3: Messaging no tiene circuit breaker")
    void messagingSinCircuitBreaker() {
        AtomicInteger attempts = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            callFailing(Provider.MESSAGING, attempts);
        }
        int before = attempts.get();

        callFailing(Provider.MESSAGING, attempts);
        assertThat(attempts.get() - before).as("sigue intentando: no hay circuito que abrir").isEqualTo(3);
    }

    @Test
    @DisplayName("CA-16: el correlationId viaja al hilo que llama al proveedor")
    void propagaCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-12345678");

        String seen = caller.call(Provider.MESSAGING, "sendEmail", () -> MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seen).isEqualTo("corr-12345678");
    }

    private void callFailing(Provider provider, AtomicInteger attempts) {
        try {
            caller.call(provider, "op", () -> {
                attempts.incrementAndGet();
                throw transientFailure(provider);
            });
        } catch (ProviderException e) {
            if ("circuito abierto".equals(e.getMessage())) {
                throw e;
            }
        }
    }

    private static ProviderException transientFailure(Provider provider) {
        return new ProviderException(provider, ProviderException.Kind.UNAVAILABLE, "503");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
