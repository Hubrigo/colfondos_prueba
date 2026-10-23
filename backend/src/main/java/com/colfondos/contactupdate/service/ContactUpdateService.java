package com.colfondos.contactupdate.service;

import com.colfondos.contactupdate.api.FlowResponse;
import com.colfondos.contactupdate.api.StartFlowRequest;
import com.colfondos.contactupdate.api.UpdateEmailRequest;
import com.colfondos.contactupdate.api.UpdateEmailResponse;
import com.colfondos.contactupdate.api.UpdateEmailResponse.NotificationStatus;
import com.colfondos.contactupdate.api.VerifyOtpRequest;
import com.colfondos.contactupdate.api.VerifyOtpResponse;
import com.colfondos.contactupdate.audit.AuditEvent.Result;
import com.colfondos.contactupdate.audit.AuditEvent.Type;
import com.colfondos.contactupdate.audit.AuditService;
import com.colfondos.contactupdate.client.CustomerDataClient;
import com.colfondos.contactupdate.client.CustomerDataClient.Customer;
import com.colfondos.contactupdate.client.CustomerDataClient.UpdateResult;
import com.colfondos.contactupdate.client.IdentityVerificationClient;
import com.colfondos.contactupdate.client.IdentityVerificationClient.Channel;
import com.colfondos.contactupdate.client.IdentityVerificationClient.OtpChallenge;
import com.colfondos.contactupdate.client.IdentityVerificationClient.OtpValidation;
import com.colfondos.contactupdate.client.MessagingClient;
import com.colfondos.contactupdate.client.MessagingClient.EmailNotification;
import com.colfondos.contactupdate.client.MessagingClient.Template;
import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.config.ContactUpdateProperties;
import com.colfondos.contactupdate.error.BusinessException;
import com.colfondos.contactupdate.error.ErrorCode;
import com.colfondos.contactupdate.observability.Masking;
import com.colfondos.contactupdate.resilience.ResilientCaller;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Orquesta la HU-CONTACT-001: identificación → OTP → actualización → notificación,
 * con el orden de pasos impuesto en el servidor (CA-10) y trazabilidad en cada paso.
 */
@Service
public class ContactUpdateService {

    static final String GENERIC_START_MESSAGE =
            "Si los datos corresponden a un afiliado, enviamos un código al celular registrado.";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final CustomerDataClient customerData;
    private final IdentityVerificationClient identity;
    private final MessagingClient messaging;
    private final ResilientCaller resilient;
    private final FlowStore flowStore;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final ContactUpdateProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /**
     * {@code properties} va primero a propósito: se valida antes de resolver los adaptadores, así
     * un despliegue sin variables falla nombrando la variable y no un bean faltante (INC-004).
     */
    public ContactUpdateService(ContactUpdateProperties properties, CustomerDataClient customerData,
                                IdentityVerificationClient identity, MessagingClient messaging,
                                ResilientCaller resilient, FlowStore flowStore, RateLimiter rateLimiter,
                                AuditService audit, Clock clock, MeterRegistry meterRegistry) {
        this.customerData = customerData;
        this.identity = identity;
        this.messaging = messaging;
        this.resilient = resilient;
        this.flowStore = flowStore;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    // ---------------------------------------------------------------- paso 1: identificación + OTP

    public FlowResponse startFlow(StartFlowRequest request, String browser, String clientIp) {
        String type = request.documentType().name();
        String customerRef = Masking.customerRef(type, request.documentNumber());
        OffsetDateTime now = now();
        ContactFlow flow = new ContactFlow(UUID.randomUUID().toString(), customerRef, browser,
                now.plus(properties.flowTtl()));

        if (!rateLimiter.tryAcquire(customerRef, clientIp)) {
            audit.record(flow, Type.SECURITY_EVENT, Result.FAILURE, null, "RATE_LIMITED");
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Has superado el número de intentos. Intenta más tarde.");
        }

        try {
            Optional<Customer> customer = resilient.call(Provider.CUSTOMER_DATA, "findByDocument",
                    () -> customerData.findByDocument(type, request.documentNumber()));

            if (customer.isPresent() && "ACTIVE".equals(customer.get().status())) {
                flow.attachCustomer(customer.get().customerId(), customer.get().email());
                OtpChallenge challenge = resilient.call(Provider.IDENTITY_CAPABILITY, "generateOtp",
                        () -> identity.generateOtp(customer.get().customerId(), Channel.SMS));
                flow.otpIssued(challenge.challengeId(), challenge.expiresAt(), now.plus(cooldown()));
                audit.record(flow, Type.FLOW_STARTED, Result.SUCCESS, Provider.CUSTOMER_DATA, null);
                audit.record(flow, Type.OTP_SENT, Result.SUCCESS, Provider.IDENTITY_CAPABILITY, "SMS");
            } else {
                // Flujo señuelo: misma respuesta y mismos tiempos de vida, pero nunca valida (anti-enumeración).
                flow.otpIssued(null, now.plus(properties.otp().ttl()), now.plus(cooldown()));
                audit.record(flow, Type.FLOW_STARTED, Result.FAILURE, Provider.CUSTOMER_DATA, "afiliado no habilitado");
            }
        } catch (ProviderException e) {
            audit.record(flow, Type.FLOW_STARTED, Result.FAILURE, e.provider(), e.kind().name());
            countFlow("provider_error", browser);
            throw e;
        }

        flowStore.save(flow);
        return toFlowResponse(flow);
    }

    public FlowResponse resendOtp(String flowId) {
        ContactFlow flow = requireFlow(flowId);
        synchronized (flow) {
            ensureNotBlocked(flow);
            if (flow.status() != ContactFlow.Status.OTP_SENT) {
                throw new BusinessException(ErrorCode.FLOW_NOT_FOUND, "Este proceso ya no admite reenvío de código.");
            }
            OffsetDateTime now = now();
            if (now.isBefore(flow.resendAvailableAt())) {
                long seconds = Math.max(1, Duration.between(now, flow.resendAvailableAt()).toSeconds());
                throw new BusinessException(ErrorCode.OTP_RESEND_TOO_SOON,
                        "Podrás solicitar un nuevo código en " + seconds + " segundos.",
                        Map.of("retryAfterSeconds", seconds));
            }
            if (flow.resendCount() >= properties.otp().maxResends()) {
                audit.record(flow, Type.SECURITY_EVENT, Result.FAILURE, null, "MAX_RESENDS");
                throw new BusinessException(ErrorCode.RATE_LIMITED, "Alcanzaste el máximo de reenvíos. Intenta más tarde.");
            }
            if (flow.isDecoy()) {
                flow.otpResent(null, now.plus(properties.otp().ttl()), now.plus(cooldown()));
            } else {
                OtpChallenge challenge = resilient.call(Provider.IDENTITY_CAPABILITY, "generateOtp",
                        () -> identity.generateOtp(flow.customerId(), Channel.SMS));
                flow.otpResent(challenge.challengeId(), challenge.expiresAt(), now.plus(cooldown()));
                audit.record(flow, Type.OTP_SENT, Result.SUCCESS, Provider.IDENTITY_CAPABILITY, "reenvío");
            }
            return toFlowResponse(flow);
        }
    }

    // ---------------------------------------------------------------- paso 2: validación del OTP

    public VerifyOtpResponse verifyOtp(String flowId, VerifyOtpRequest request) {
        ContactFlow flow = requireFlow(flowId);
        synchronized (flow) {
            ensureNotBlocked(flow);
            if (flow.status() == ContactFlow.Status.VERIFIED) {
                return new VerifyOtpResponse(true, flow.verifiedUntil());
            }
            if (flow.status() == ContactFlow.Status.COMPLETED) {
                throw new BusinessException(ErrorCode.FLOW_NOT_FOUND, "Este proceso ya finalizó.");
            }
            return flow.isDecoy() ? verifyDecoy(flow) : verifyWithProvider(flow, request.code());
        }
    }

    private VerifyOtpResponse verifyWithProvider(ContactFlow flow, String code) {
        OtpValidation validation = resilient.call(Provider.IDENTITY_CAPABILITY, "validateOtp",
                () -> identity.validateOtp(flow.challengeId(), code));
        return switch (validation.result()) {
            case VALID -> {
                flow.verified(validation.verificationToken(), now().plus(properties.otp().ttl()));
                audit.record(flow, Type.OTP_VERIFIED, Result.SUCCESS, Provider.IDENTITY_CAPABILITY, null);
                yield new VerifyOtpResponse(true, flow.verifiedUntil());
            }
            case INVALID -> {
                if (validation.remainingAttempts() <= 0) {
                    throw blockFlow(flow);
                }
                throw otpInvalid(flow, validation.remainingAttempts());
            }
            case EXPIRED -> {
                audit.record(flow, Type.OTP_FAILED, Result.FAILURE, Provider.IDENTITY_CAPABILITY, "EXPIRED");
                throw new BusinessException(ErrorCode.OTP_EXPIRED, "Tu código expiró. Solicita uno nuevo.");
            }
            case BLOCKED -> throw blockFlow(flow);
        };
    }

    private VerifyOtpResponse verifyDecoy(ContactFlow flow) {
        if (now().isAfter(flow.otpExpiresAt())) {
            throw new BusinessException(ErrorCode.OTP_EXPIRED, "Tu código expiró. Solicita uno nuevo.");
        }
        int remaining = properties.otp().maxAttempts() - flow.registerDecoyFailure();
        if (remaining <= 0) {
            throw blockFlow(flow);
        }
        throw otpInvalid(flow, remaining);
    }

    private BusinessException otpInvalid(ContactFlow flow, int remaining) {
        audit.record(flow, Type.OTP_FAILED, Result.FAILURE, Provider.IDENTITY_CAPABILITY, "INVALID");
        String attempts = remaining == 1 ? "1 intento" : remaining + " intentos";
        return new BusinessException(ErrorCode.OTP_INVALID, "Código incorrecto. Te quedan " + attempts + ".",
                Map.of("remainingAttempts", remaining));
    }

    private BusinessException blockFlow(ContactFlow flow) {
        flow.block();
        audit.record(flow, Type.OTP_BLOCKED, Result.FAILURE, Provider.IDENTITY_CAPABILITY, null);
        audit.record(flow, Type.SECURITY_EVENT, Result.FAILURE, null, "OTP_BLOCKED");
        countFlow("blocked", flow.browser());
        return new BusinessException(ErrorCode.OTP_BLOCKED,
                "Por seguridad bloqueamos el proceso. Comunícate con la línea de servicio o acércate a una oficina.");
    }

    // ---------------------------------------------------------------- paso 3: actualización + notificación

    public UpdateEmailResponse updateEmail(String flowId, UpdateEmailRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Falta o es inválido el header Idempotency-Key.");
        }
        ContactFlow flow = requireFlow(flowId);
        synchronized (flow) {
            Optional<UpdateEmailResponse> previous = flow.resultFor(idempotencyKey);
            if (previous.isPresent()) {
                return previous.get();
            }
            if (flow.status() != ContactFlow.Status.VERIFIED || now().isAfter(flow.verifiedUntil())) {
                audit.record(flow, Type.SECURITY_EVENT, Result.FAILURE, null, "IDENTITY_NOT_VERIFIED");
                throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED,
                        "Debes validar tu identidad antes de actualizar el correo.");
            }
            String newEmail = normalizeEmail(request.newEmail());
            if (newEmail.equalsIgnoreCase(flow.currentEmail())) {
                throw new BusinessException(ErrorCode.EMAIL_SAME_AS_CURRENT,
                        "El nuevo correo debe ser distinto al actual.");
            }

            String token = flow.verificationToken();
            UpdateResult result = resilient.call(Provider.CUSTOMER_DATA, "updateEmail",
                    () -> customerData.updateEmail(flow.customerId(), newEmail, token, idempotencyKey));
            String previousEmail = flow.currentEmail();
            flow.completed(result.transactionId());
            audit.record(flow, Type.EMAIL_UPDATED, Result.SUCCESS, Provider.CUSTOMER_DATA, null);

            NotificationStatus notification = notifyChange(flow, newEmail, previousEmail);
            UpdateEmailResponse response = new UpdateEmailResponse(result.transactionId(), "UPDATED",
                    Masking.email(newEmail), result.updatedAt(), notification);
            flow.rememberResult(idempotencyKey, response);
            countFlow("success", flow.browser());
            return response;
        }
    }

    /** La notificación nunca revierte la actualización: si falla queda PENDING para reproceso (D-05). */
    private NotificationStatus notifyChange(ContactFlow flow, String newEmail, String previousEmail) {
        Map<String, String> params = Map.of(
                "transactionId", flow.transactionId(),
                "maskedEmail", Masking.email(newEmail));
        boolean sent = send(flow, new EmailNotification(flow.customerId(), newEmail, Template.EMAIL_UPDATED_NEW, params));
        if (previousEmail != null) {
            sent &= send(flow, new EmailNotification(flow.customerId(), previousEmail,
                    Template.EMAIL_UPDATED_SECURITY_ALERT, params));
        }
        return sent ? NotificationStatus.SENT : NotificationStatus.PENDING;
    }

    private boolean send(ContactFlow flow, EmailNotification notification) {
        try {
            resilient.call(Provider.MESSAGING, "sendEmail", () -> messaging.sendEmail(notification));
            audit.record(flow, Type.NOTIFICATION_SENT, Result.SUCCESS, Provider.MESSAGING, notification.template().name());
            return true;
        } catch (ProviderException e) {
            audit.record(flow, Type.NOTIFICATION_PENDING, Result.FAILURE, Provider.MESSAGING,
                    notification.template().name() + ":" + e.kind());
            return false;
        }
    }

    // ---------------------------------------------------------------- utilidades

    private ContactFlow requireFlow(String flowId) {
        return flowStore.find(flowId).orElseThrow(() ->
                new BusinessException(ErrorCode.FLOW_NOT_FOUND, "Tu sesión expiró. Vuelve a empezar."));
    }

    private void ensureNotBlocked(ContactFlow flow) {
        if (flow.status() == ContactFlow.Status.BLOCKED) {
            throw new BusinessException(ErrorCode.OTP_BLOCKED,
                    "Por seguridad bloqueamos el proceso. Comunícate con la línea de servicio o acércate a una oficina.");
        }
    }

    private FlowResponse toFlowResponse(ContactFlow flow) {
        return new FlowResponse(flow.flowId(), Channel.SMS.name(), flow.otpExpiresAt(),
                flow.resendAvailableAt(), GENERIC_START_MESSAGE);
    }

    static String normalizeEmail(String email) {
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        return trimmed.substring(0, at) + trimmed.substring(at).toLowerCase(Locale.ROOT);
    }

    private Duration cooldown() {
        return properties.otp().resendCooldown();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private void countFlow(String result, String browser) {
        meterRegistry.counter("contact_update_flow_total", "result", result, "browser", browser).increment();
    }
}
