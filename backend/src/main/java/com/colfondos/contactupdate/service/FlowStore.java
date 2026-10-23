package com.colfondos.contactupdate.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Almacén de flujos con TTL. En memoria para la prueba; en producción, Redis o la
 * caché corporativa, para soportar varias instancias (E2 D-04).
 */
@Component
public class FlowStore {

    private final Map<String, ContactFlow> flows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public FlowStore(Clock clock, MeterRegistry meterRegistry) {
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public void save(ContactFlow flow) {
        flows.put(flow.flowId(), flow);
    }

    /** Devuelve el flujo si existe y no ha vencido. */
    public Optional<ContactFlow> find(String flowId) {
        ContactFlow flow = flows.get(flowId);
        if (flow == null) {
            return Optional.empty();
        }
        if (flow.isExpired(OffsetDateTime.now(clock))) {
            evict(flow);
            return Optional.empty();
        }
        return Optional.of(flow);
    }

    /** Purga periódica: un flujo vencido sin completar cuenta como abandono (KPI, doc. 14). */
    @Scheduled(fixedDelayString = "PT1M")
    public void purgeExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        flows.values().stream().filter(f -> f.isExpired(now)).toList().forEach(this::evict);
    }

    private void evict(ContactFlow flow) {
        if (flows.remove(flow.flowId()) != null && flow.status() != ContactFlow.Status.COMPLETED) {
            meterRegistry.counter("contact_update_abandon_total", "lastStep", flow.status().name()).increment();
        }
    }
}
