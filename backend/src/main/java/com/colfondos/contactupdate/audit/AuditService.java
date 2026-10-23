package com.colfondos.contactupdate.audit;

import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.observability.CorrelationIdFilter;
import com.colfondos.contactupdate.service.ContactFlow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/** Registra eventos de trazabilidad y los emite como log estructurado ({@code audit=true}). */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final AuditRepository repository;
    private final Clock clock;

    public AuditService(AuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AuditEvent record(ContactFlow flow, AuditEvent.Type type, AuditEvent.Result result,
                             Provider provider, String detail) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(),
                flow.flowId(),
                flow.transactionId(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                type,
                result,
                provider,
                flow.customerRef(),
                flow.browser(),
                detail,
                OffsetDateTime.now(clock));
        repository.save(event);
        log.atInfo()
                .addKeyValue("audit", true)
                .addKeyValue("eventType", type)
                .addKeyValue("result", result)
                .addKeyValue("provider", provider)
                .addKeyValue("flowId", event.flowId())
                .addKeyValue("transactionId", event.transactionId())
                .addKeyValue("customerRef", event.customerRef())
                .addKeyValue("browser", event.browser())
                .log("Auditoría {} {}", type, result);
        return event;
    }
}
