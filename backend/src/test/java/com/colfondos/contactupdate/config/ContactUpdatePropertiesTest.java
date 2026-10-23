package com.colfondos.contactupdate.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Regresión de INC-004: una variable de ambiente faltante impide arrancar y se nombra. */
class ContactUpdatePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ContactUpdateProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues(
                    "contact-update.zone-id=America/Bogota",
                    "contact-update.flow-ttl=PT10M",
                    "contact-update.otp.ttl=PT5M",
                    "contact-update.otp.max-attempts=3",
                    "contact-update.otp.resend-cooldown=PT30S",
                    "contact-update.otp.max-resends=3",
                    "contact-update.rate-limit.max-flows-per-document=5",
                    "contact-update.rate-limit.max-flows-per-ip=30",
                    "contact-update.rate-limit.window=PT15M",
                    "contact-update.providers.customer-data.timeout=PT3S",
                    "contact-update.providers.customer-data.backoff=PT0.2S",
                    "contact-update.providers.identity.timeout=PT3S",
                    "contact-update.providers.identity.backoff=PT0.3S",
                    "contact-update.providers.messaging.timeout=PT2S",
                    "contact-update.providers.messaging.backoff=PT0.5S");

    @Test
    @DisplayName("Sin CORS ni URLs de proveedores el contexto no arranca y nombra cada propiedad")
    void faltanVariablesObligatorias() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            String message = rootMessage(context.getStartupFailure());
            assertThat(message)
                    .contains("corsAllowedOrigins")
                    .contains("providers.customerData.url")
                    .contains("providers.identity.url")
                    .contains("providers.messaging.url");
        });
    }

    @Test
    void conTodasLasVariablesArranca() {
        runner.withPropertyValues(
                        "contact-update.cors-allowed-origins=https://portal.example",
                        "contact-update.providers.customer-data.url=https://ih.example/customer",
                        "contact-update.providers.identity.url=https://ih.example/identity",
                        "contact-update.providers.messaging.url=https://ih.example/messaging")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ContactUpdateProperties.class).otp().maxAttempts()).isEqualTo(3);
                });
    }

    @Test
    void rechazaValoresFueraDeRango() {
        runner.withPropertyValues(
                        "contact-update.cors-allowed-origins=https://portal.example",
                        "contact-update.providers.customer-data.url=x",
                        "contact-update.providers.identity.url=x",
                        "contact-update.providers.messaging.url=x",
                        "contact-update.otp.max-attempts=0")
                .run(context -> assertThat(rootMessage(context.getStartupFailure())).contains("otp.maxAttempts"));
    }

    private static String rootMessage(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
