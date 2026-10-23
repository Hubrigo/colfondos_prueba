package com.colfondos.contactupdate.support;

import com.colfondos.contactupdate.config.ContactUpdateProperties;
import com.colfondos.contactupdate.config.ContactUpdateProperties.Otp;
import com.colfondos.contactupdate.config.ContactUpdateProperties.Provider;
import com.colfondos.contactupdate.config.ContactUpdateProperties.Providers;
import com.colfondos.contactupdate.config.ContactUpdateProperties.RateLimit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Datos y utilidades compartidas por las pruebas. */
public final class TestFixtures {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private TestFixtures() {
    }

    /** Propiedades equivalentes a application.yml, con timeouts cortos para pruebas rápidas. */
    public static ContactUpdateProperties properties() {
        return new ContactUpdateProperties(
                "America/Bogota",
                List.of("http://localhost:5173"),
                Duration.ofMinutes(10),
                new Otp(Duration.ofMinutes(5), 3, Duration.ofSeconds(30), 3),
                new RateLimit(5, 30, Duration.ofMinutes(15)),
                new Providers(provider(true), provider(true), provider(false)));
    }

    public static Provider provider(boolean circuitBreaker) {
        return new Provider("sim://test", Duration.ofMillis(200), 2, Duration.ofMillis(10), circuitBreaker);
    }

    /** Reloj que la prueba adelanta a voluntad (vigencia del OTP, INC-003). */
    public static final class MutableClock extends Clock {

        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return BOGOTA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
