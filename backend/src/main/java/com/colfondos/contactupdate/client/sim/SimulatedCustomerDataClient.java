package com.colfondos.contactupdate.client.sim;

import com.colfondos.contactupdate.client.CustomerDataClient;
import com.colfondos.contactupdate.error.BusinessException;
import com.colfondos.contactupdate.error.ErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Simula Customer Data API con datos sintéticos, idempotencia por clave y latencia configurable. */
@Profile("sim")
@Component
public class SimulatedCustomerDataClient implements CustomerDataClient {

    private final Map<String, Customer> byDocument = new ConcurrentHashMap<>();
    private final Map<String, Customer> byId = new ConcurrentHashMap<>();
    private final Map<String, UpdateResult> processedKeys = new ConcurrentHashMap<>();
    private final Set<String> slowCallServed = ConcurrentHashMap.newKeySet();
    private final SimulatedIdentityClient identity;
    private final SimulationProperties simulation;
    private final Clock clock;

    public SimulatedCustomerDataClient(SimulatedIdentityClient identity, SimulationProperties simulation, Clock clock) {
        this.identity = identity;
        this.simulation = simulation;
        this.clock = clock;
        SimulationScenarios.customers().forEach(c -> {
            byDocument.put(key(c.documentType(), c.documentNumber()), c.customer());
            byId.put(c.customer().customerId(), c.customer());
        });
    }

    @Override
    public Optional<Customer> findByDocument(String documentType, String documentNumber) {
        return Optional.ofNullable(byDocument.get(key(documentType, documentNumber)));
    }

    @Override
    public UpdateResult updateEmail(String customerId, String newEmail, String verificationToken, String idempotencyKey) {
        // CA-12: el primer intento de este afiliado tarda más que el timeout del orquestador.
        if (SimulationScenarios.SLOW_UPDATE.equals(customerId) && slowCallServed.add(customerId)) {
            sleep();
        }
        synchronized (this) {
            UpdateResult previous = processedKeys.get(idempotencyKey);
            if (previous != null) {
                return previous;
            }
            if (!identity.consumeToken(customerId, verificationToken)) {
                // El proveedor real respondería 403: es un rechazo de negocio, no se reintenta.
                throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED, "Validación de identidad no vigente.");
            }
            Customer current = byId.get(customerId);
            Customer updated = new Customer(customerId, newEmail, current.mobilePhone(), current.status());
            byId.put(customerId, updated);
            byDocument.replaceAll((k, c) -> c.customerId().equals(customerId) ? updated : c);

            OffsetDateTime now = OffsetDateTime.now(clock);
            String transactionId = "TX-" + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            UpdateResult result = new UpdateResult(transactionId, now);
            processedKeys.put(idempotencyKey, result);
            return result;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(simulation.slowCallDelay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String key(String documentType, String documentNumber) {
        return documentType + ":" + documentNumber;
    }
}
