package com.colfondos.contactupdate.api;

import com.colfondos.contactupdate.audit.AuditEvent;
import com.colfondos.contactupdate.audit.AuditRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de la trazabilidad de un flujo para la demo y la sustentación. Solo en el perfil {@code sim}. */
@Profile("sim")
@RestController
@RequestMapping("/api/v1/contact-update/flows")
public class SimulationAuditController {

    private final AuditRepository repository;

    public SimulationAuditController(AuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{flowId}/audit")
    public List<AuditEvent> audit(@PathVariable String flowId) {
        return repository.findByFlowId(flowId);
    }
}
