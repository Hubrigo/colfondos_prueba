package com.colfondos.contactupdate.audit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Almacén de auditoría en memoria, acotado, para la prueba. En producción se reemplaza
 * por el almacén corporativo de auditoría (pregunta L-05 al LT).
 */
@Repository
public class AuditRepository {

    private static final int MAX_EVENTS = 10_000;

    private final Deque<AuditEvent> events = new ArrayDeque<>();

    public synchronized void save(AuditEvent event) {
        if (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<AuditEvent> findByFlowId(String flowId) {
        return events.stream().filter(e -> flowId.equals(e.flowId())).toList();
    }

    public synchronized List<AuditEvent> findAll() {
        return List.copyOf(events);
    }
}
