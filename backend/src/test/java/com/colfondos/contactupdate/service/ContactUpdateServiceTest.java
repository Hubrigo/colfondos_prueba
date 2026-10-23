package com.colfondos.contactupdate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colfondos.contactupdate.api.FlowResponse;
import com.colfondos.contactupdate.api.StartFlowRequest;
import com.colfondos.contactupdate.api.StartFlowRequest.DocumentType;
import com.colfondos.contactupdate.api.UpdateEmailRequest;
import com.colfondos.contactupdate.api.UpdateEmailResponse;
import com.colfondos.contactupdate.api.UpdateEmailResponse.NotificationStatus;
import com.colfondos.contactupdate.api.VerifyOtpRequest;
import com.colfondos.contactupdate.audit.AuditEvent;
import com.colfondos.contactupdate.audit.AuditService;
import com.colfondos.contactupdate.client.CustomerDataClient;
import com.colfondos.contactupdate.client.CustomerDataClient.Customer;
import com.colfondos.contactupdate.client.CustomerDataClient.UpdateResult;
import com.colfondos.contactupdate.client.IdentityVerificationClient;
import com.colfondos.contactupdate.client.IdentityVerificationClient.Channel;
import com.colfondos.contactupdate.client.IdentityVerificationClient.OtpChallenge;
import com.colfondos.contactupdate.client.IdentityVerificationClient.OtpValidation;
import com.colfondos.contactupdate.client.IdentityVerificationClient.Result;
import com.colfondos.contactupdate.client.MessagingClient;
import com.colfondos.contactupdate.client.MessagingClient.EmailNotification;
import com.colfondos.contactupdate.client.MessagingClient.Template;
import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.config.ContactUpdateProperties;
import com.colfondos.contactupdate.error.BusinessException;
import com.colfondos.contactupdate.error.ErrorCode;
import com.colfondos.contactupdate.resilience.ResilientCaller;
import com.colfondos.contactupdate.support.TestFixtures;
import com.colfondos.contactupdate.support.TestFixtures.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Pruebas unitarias del orquestador: reglas de la HU con proveedores simulados por Mockito. */
class ContactUpdateServiceTest {

    private static final String DOC = "1012345678";
    private static final String CUSTOMER_ID = "AF-1001";
    private static final String CURRENT_EMAIL = "ana.perez@correo.test";
    private static final String TOKEN = "token-verificacion";

    private final ContactUpdateProperties properties = TestFixtures.properties();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T15:00:00Z"));
    private final CustomerDataClient customerData = mock(CustomerDataClient.class);
    private final IdentityVerificationClient identity = mock(IdentityVerificationClient.class);
    private final MessagingClient messaging = mock(MessagingClient.class);
    private final ResilientCaller resilient = mock(ResilientCaller.class);
    private final AuditService audit = mock(AuditService.class);
    private ContactUpdateService service;

    @BeforeEach
    void setUp() {
        // El ResilientCaller se prueba aparte: aquí solo ejecuta la llamada.
        when(resilient.call(any(), anyString(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(2).get());
        var meters = new SimpleMeterRegistry();
        service = new ContactUpdateService(properties, customerData, identity, messaging, resilient,
                new FlowStore(clock, meters), new RateLimiter(properties, clock), audit, clock, meters);

        when(customerData.findByDocument("CC", DOC))
                .thenReturn(Optional.of(new Customer(CUSTOMER_ID, CURRENT_EMAIL, "3001234567", "ACTIVE")));
        when(identity.generateOtp(eq(CUSTOMER_ID), eq(Channel.SMS)))
                .thenAnswer(inv -> new OtpChallenge("ch-1", now().plusMinutes(5)));
        when(customerData.updateEmail(eq(CUSTOMER_ID), anyString(), eq(TOKEN), anyString()))
                .thenAnswer(inv -> new UpdateResult("TX-1", now()));
    }

    @Nested
    @DisplayName("Paso 1: identificación")
    class Identification {

        @Test
        void afiliadoActivoRecibeOtpPorSmsYQuedaAuditado() {
            FlowResponse response = start(DOC);

            assertThat(response.otpChannel()).isEqualTo("SMS");
            assertThat(response.otpExpiresAt()).isEqualTo(now().plusMinutes(5));
            assertThat(response.resendAvailableAt()).isEqualTo(now().plusSeconds(30));
            verify(identity).generateOtp(CUSTOMER_ID, Channel.SMS);
            verifyAudited(AuditEvent.Type.OTP_SENT, AuditEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("CA-03: documento inexistente responde igual y no genera OTP")
        void documentoInexistenteNoRevelaExistencia() {
            FlowResponse real = start(DOC);
            FlowResponse ghost = start("5555555555");

            assertThat(ghost.message()).isEqualTo(real.message());
            assertThat(ghost.otpChannel()).isEqualTo(real.otpChannel());
            assertThat(ghost.otpExpiresAt()).isEqualTo(real.otpExpiresAt());
            verify(identity, times(1)).generateOtp(anyString(), any());
        }

        @Test
        void sextoIntentoDelMismoDocumentoEsLimitado() {
            for (int i = 0; i < 5; i++) {
                start(DOC);
            }
            assertCode(() -> start(DOC), ErrorCode.RATE_LIMITED);
            verifyAudited(AuditEvent.Type.SECURITY_EVENT, AuditEvent.Result.FAILURE);
        }

        @Test
        @DisplayName("CA-13: proveedor de identidad caído se propaga y se audita")
        void proveedorCaidoSePropaga() {
            when(identity.generateOtp(anyString(), any()))
                    .thenThrow(new ProviderException(Provider.IDENTITY_CAPABILITY, ProviderException.Kind.UNAVAILABLE, "503"));

            assertThatThrownBy(() -> start(DOC)).isInstanceOf(ProviderException.class);
            verifyAudited(AuditEvent.Type.FLOW_STARTED, AuditEvent.Result.FAILURE);
        }
    }

    @Nested
    @DisplayName("Paso 2: validación del OTP")
    class OtpValidationStep {

        @Test
        void otpValidoVerificaElFlujo() {
            String flowId = start(DOC).flowId();
            givenValidation(new OtpValidation(Result.VALID, 3, TOKEN));

            assertThat(service.verifyOtp(flowId, otp()).verified()).isTrue();
            verifyAudited(AuditEvent.Type.OTP_VERIFIED, AuditEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("CA-04: OTP incorrecto informa intentos restantes")
        void otpIncorrectoInformaIntentos() {
            String flowId = start(DOC).flowId();
            givenValidation(new OtpValidation(Result.INVALID, 2, null));

            BusinessException ex = assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_INVALID);
            assertThat(ex.extra()).containsEntry("remainingAttempts", 2);
            assertThat(ex.getMessage()).contains("Te quedan 2 intentos");
        }

        @Test
        @DisplayName("CA-05: sin intentos el flujo queda bloqueado aun con el código correcto")
        void sinIntentosBloquea() {
            String flowId = start(DOC).flowId();
            givenValidation(new OtpValidation(Result.INVALID, 0, null));

            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_BLOCKED);
            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_BLOCKED);
            verify(identity, times(1)).validateOtp(anyString(), anyString());
            verifyAudited(AuditEvent.Type.SECURITY_EVENT, AuditEvent.Result.FAILURE);
        }

        @Test
        @DisplayName("CA-06: OTP expirado según el proveedor")
        void otpExpirado() {
            String flowId = start(DOC).flowId();
            givenValidation(new OtpValidation(Result.EXPIRED, 0, null));

            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_EXPIRED);
        }

        @Test
        void flujoSenueloSeBloqueaSinLlamarAlProveedor() {
            String flowId = start("5555555555").flowId();

            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_INVALID);
            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_INVALID);
            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.OTP_BLOCKED);
            verify(identity, never()).validateOtp(any(), any());
        }

        @Test
        void reenvioAntesDeLaEsperaSeRechazaYDespuesSePermiteHastaElMaximo() {
            String flowId = start(DOC).flowId();
            BusinessException tooSoon = assertCode(() -> service.resendOtp(flowId), ErrorCode.OTP_RESEND_TOO_SOON);
            assertThat(tooSoon.extra()).containsEntry("retryAfterSeconds", 30L);

            for (int i = 0; i < 3; i++) {
                clock.advance(Duration.ofSeconds(31));
                service.resendOtp(flowId);
            }
            clock.advance(Duration.ofSeconds(31));
            assertCode(() -> service.resendOtp(flowId), ErrorCode.RATE_LIMITED);
            verify(identity, times(4)).generateOtp(CUSTOMER_ID, Channel.SMS);
        }

        @Test
        void flujoVencidoNoExiste() {
            String flowId = start(DOC).flowId();
            clock.advance(Duration.ofMinutes(11));

            assertCode(() -> service.verifyOtp(flowId, otp()), ErrorCode.FLOW_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Paso 3: actualización y notificación")
    class Update {

        @Test
        @DisplayName("CA-01: actualiza, notifica al correo nuevo y avisa al anterior")
        void actualizacionExitosa() {
            String flowId = verifiedFlow();

            UpdateEmailResponse response = update(flowId, "Nuevo.Correo@Dominio.COM", "key-00000001");

            assertThat(response.status()).isEqualTo("UPDATED");
            assertThat(response.transactionId()).isEqualTo("TX-1");
            assertThat(response.maskedEmail()).isEqualTo("Nu***@dominio.com");
            assertThat(response.notificationStatus()).isEqualTo(NotificationStatus.SENT);
            // El dominio se normaliza a minúsculas; la parte local se conserva.
            verify(customerData).updateEmail(CUSTOMER_ID, "Nuevo.Correo@dominio.com", TOKEN, "key-00000001");

            ArgumentCaptor<EmailNotification> sent = ArgumentCaptor.forClass(EmailNotification.class);
            verify(messaging, times(2)).sendEmail(sent.capture());
            assertThat(sent.getAllValues()).extracting(EmailNotification::template, EmailNotification::to)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(Template.EMAIL_UPDATED_NEW, "Nuevo.Correo@dominio.com"),
                            org.assertj.core.groups.Tuple.tuple(Template.EMAIL_UPDATED_SECURITY_ALERT, CURRENT_EMAIL));
            verifyAudited(AuditEvent.Type.EMAIL_UPDATED, AuditEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("CA-10: sin OTP validado no se llama a Customer Data")
        void sinValidacionDeIdentidad() {
            String flowId = start(DOC).flowId();

            assertCode(() -> update(flowId, "otro@dominio.com", "key-00000002"), ErrorCode.IDENTITY_NOT_VERIFIED);
            verify(customerData, never()).updateEmail(any(), any(), any(), any());
            verifyAudited(AuditEvent.Type.SECURITY_EVENT, AuditEvent.Result.FAILURE);
        }

        @Test
        void validacionVencidaExigeNuevaValidacion() {
            String flowId = verifiedFlow();
            clock.advance(Duration.ofMinutes(6));

            assertCode(() -> update(flowId, "otro@dominio.com", "key-00000003"), ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        @Test
        @DisplayName("CA-08: correo igual al actual, sin importar mayúsculas")
        void correoIgualAlActual() {
            String flowId = verifiedFlow();

            assertCode(() -> update(flowId, "ANA.PEREZ@correo.test", "key-00000004"), ErrorCode.EMAIL_SAME_AS_CURRENT);
        }

        @Test
        void mismaClaveDeIdempotenciaNoActualizaDosVeces() {
            String flowId = verifiedFlow();

            UpdateEmailResponse first = update(flowId, "nuevo@dominio.com", "key-00000005");
            UpdateEmailResponse retry = update(flowId, "nuevo@dominio.com", "key-00000005");

            assertThat(retry).isSameAs(first);
            verify(customerData, times(1)).updateEmail(any(), any(), any(), any());
        }

        @Test
        void tokenEsDeUnSoloUso() {
            String flowId = verifiedFlow();
            update(flowId, "nuevo@dominio.com", "key-00000006");

            assertCode(() -> update(flowId, "otro@dominio.com", "key-00000007"), ErrorCode.IDENTITY_NOT_VERIFIED);
        }

        @Test
        @DisplayName("CA-14: si Messaging falla la actualización se mantiene y queda PENDING")
        void messagingCaidoNoRevierte() {
            String flowId = verifiedFlow();
            when(messaging.sendEmail(any()))
                    .thenThrow(new ProviderException(Provider.MESSAGING, ProviderException.Kind.UNAVAILABLE, "503"));

            UpdateEmailResponse response = update(flowId, "nuevo@dominio.com", "key-00000008");

            assertThat(response.status()).isEqualTo("UPDATED");
            assertThat(response.notificationStatus()).isEqualTo(NotificationStatus.PENDING);
            verifyAudited(AuditEvent.Type.NOTIFICATION_PENDING, AuditEvent.Result.FAILURE);
        }

        @Test
        void claveDeIdempotenciaInvalidaSeRechaza() {
            String flowId = verifiedFlow();

            assertCode(() -> update(flowId, "nuevo@dominio.com", "x"), ErrorCode.VALIDATION_ERROR);
            assertCode(() -> update(flowId, "nuevo@dominio.com", null), ErrorCode.VALIDATION_ERROR);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private FlowResponse start(String documentNumber) {
        return service.startFlow(new StartFlowRequest(DocumentType.CC, documentNumber), "Safari 17", "10.0.0.1");
    }

    private String verifiedFlow() {
        String flowId = start(DOC).flowId();
        givenValidation(new OtpValidation(Result.VALID, 3, TOKEN));
        service.verifyOtp(flowId, otp());
        return flowId;
    }

    private UpdateEmailResponse update(String flowId, String email, String key) {
        return service.updateEmail(flowId, new UpdateEmailRequest(email, email), key);
    }

    private void givenValidation(OtpValidation validation) {
        when(identity.validateOtp(eq("ch-1"), anyString())).thenReturn(validation);
    }

    private static VerifyOtpRequest otp() {
        return new VerifyOtpRequest("123456");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static BusinessException assertCode(Runnable action, ErrorCode expected) {
        try {
            action.run();
        } catch (BusinessException ex) {
            assertThat(ex.code()).isEqualTo(expected);
            return ex;
        }
        throw new AssertionError("Se esperaba BusinessException " + expected);
    }

    private void verifyAudited(AuditEvent.Type type, AuditEvent.Result result) {
        verify(audit, org.mockito.Mockito.atLeastOnce()).record(any(), eq(type), eq(result), any(), any());
    }
}
