package com.colfondos.contactupdate.api;

import com.colfondos.contactupdate.observability.BrowserDetector;
import com.colfondos.contactupdate.service.ContactUpdateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Contrato REST del orquestador (E2 §3). Sin lógica: valida la entrada y delega en el servicio. */
@RestController
@RequestMapping("/api/v1/contact-update/flows")
public class ContactUpdateController {

    private final ContactUpdateService service;

    public ContactUpdateController(ContactUpdateService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowResponse start(@Valid @RequestBody StartFlowRequest request, HttpServletRequest http) {
        return service.startFlow(request, BrowserDetector.detect(http.getHeader(HttpHeaders.USER_AGENT)),
                http.getRemoteAddr());
    }

    @PostMapping("/{flowId}/otp/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FlowResponse resend(@PathVariable String flowId) {
        return service.resendOtp(flowId);
    }

    @PostMapping("/{flowId}/otp/verify")
    public VerifyOtpResponse verify(@PathVariable String flowId, @Valid @RequestBody VerifyOtpRequest request) {
        return service.verifyOtp(flowId, request);
    }

    @PutMapping("/{flowId}/email")
    public UpdateEmailResponse updateEmail(@PathVariable String flowId,
                                           @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                           @Valid @RequestBody UpdateEmailRequest request) {
        return service.updateEmail(flowId, request, idempotencyKey);
    }
}
