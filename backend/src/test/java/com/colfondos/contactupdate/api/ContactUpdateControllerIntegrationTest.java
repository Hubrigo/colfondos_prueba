package com.colfondos.contactupdate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Integración HTTP → servicio → adaptadores simulados (perfil sim), con MockMvc.
 * Verifica el contrato de E2 §3, el formato de errores de E2 §5 y el correlationId (CA-16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sim")
@TestPropertySource(properties = {
        "contact-update.providers.customer-data.timeout=PT0.3S",
        "contact-update.providers.customer-data.backoff=PT0.01S",
        "contact-update.providers.identity.backoff=PT0.01S",
        "contact-update.providers.messaging.backoff=PT0.01S",
        "contact-update.simulation.slow-call-delay=PT0.8S",
        "contact-update.rate-limit.max-flows-per-document=100",
        "contact-update.rate-limit.max-flows-per-ip=500"})
class ContactUpdateControllerIntegrationTest {

    private static final String FLOWS = "/api/v1/contact-update/flows";
    private static final String SAFARI_17 = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15";

    @Autowired
    private MockMvc mvc;

    /** Como el frontend: un mismo correlationId en todas las llamadas del flujo (si la prueba lo fija). */
    private String flowCorrelationId;

    @Test
    @DisplayName("CA-01 + CA-16: flujo completo con el mismo correlationId y auditoría")
    void flujoCompleto() throws Exception {
        String correlationId = "it-" + UUID.randomUUID();
        flowCorrelationId = correlationId;
        String flowId = startFlow("1012345678", correlationId)
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.otpChannel").value("SMS"))
                .andExpect(jsonPath("$.otpExpiresAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*-05:00")))
                .andReturn().getResponse().getContentAsString().transform(json -> JsonPath.read(json, "$.flowId"));

        verifyOtp(flowId, "123456").andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));

        String email = unique("nuevo");
        updateEmail(flowId, email, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"))
                .andExpect(jsonPath("$.transactionId", matchesPattern("TX-\\d{8}-[0-9A-F]{6}")))
                .andExpect(jsonPath("$.maskedEmail").value("nu***@dominio.com"))
                .andExpect(jsonPath("$.updatedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*-05:00")))
                .andExpect(jsonPath("$.notificationStatus").value("SENT"));

        mvc.perform(get(FLOWS + "/" + flowId + "/audit"))
                .andExpect(jsonPath("$[*].type", hasItem("EMAIL_UPDATED")))
                .andExpect(jsonPath("$[*].correlationId", not(hasItem(not(correlationId)))))
                .andExpect(jsonPath("$[0].browser").value("Safari 17"))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString())
                        .as("CA-11: sin OTP ni correo en claro en la auditoría")
                        .doesNotContain("123456").doesNotContain(email));
    }

    @Test
    @DisplayName("CA-02: error de validación con ProblemDetail, code, errores por campo y correlationId")
    void validacionDevuelveProblemDetail() throws Exception {
        startFlow("12AB", null)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.type").value("https://colfondos.example/errors/validation-error"))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.errors[*].field", hasItem("documentNumber")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rechazaPropiedadesDesconocidasYTiposInvalidos() throws Exception {
        postJson(FLOWS, "{\"documentType\":\"CC\",\"documentNumber\":\"1012345678\",\"admin\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        postJson(FLOWS, "{\"documentType\":\"XX\",\"documentNumber\":\"1012345678\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Un X-Correlation-Id con caracteres peligrosos se reemplaza (inyección en logs)")
    void correlationIdInseguroSeReemplaza() throws Exception {
        startFlow("1012345678", "abc\r\nFAKE-LOG-LINE")
                .andExpect(header().string("X-Correlation-Id",
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    @DisplayName("CA-08 / CA-09: correo inválido y confirmación distinta")
    void validacionesDeCorreo() throws Exception {
        String flowId = verifiedFlow("1012345678");
        putEmail(flowId, "{\"newEmail\":\"a@b\",\"confirmEmail\":\"a@b\"}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", hasItem("Ingresa un correo válido")));
        putEmail(flowId, "{\"newEmail\":\"a@dominio.com\",\"confirmEmail\":\"b@dominio.com\"}", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", hasItem("Los correos no coinciden")));
    }

    @Test
    void faltaIdempotencyKey() throws Exception {
        String flowId = verifiedFlow("1012345678");
        putEmail(flowId, "{\"newEmail\":\"x@dominio.com\",\"confirmEmail\":\"x@dominio.com\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("CA-10: actualizar sin validar identidad → 403")
    void sinIdentidadValidada() throws Exception {
        String flowId = flowIdOf(startFlow("1012345678", null));
        updateEmail(flowId, unique("x"), UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("CA-04 / CA-05: intentos restantes y bloqueo con 423")
    void otpIncorrectoYBloqueo() throws Exception {
        String flowId = flowIdOf(startFlow("1012345678", null));
        verifyOtp(flowId, "000000").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts").value(2));
        verifyOtp(flowId, "000000");
        verifyOtp(flowId, "000000").andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("OTP_BLOCKED"));
    }

    @Test
    @DisplayName("CA-12: timeout del primer intento, reintento idempotente y 200")
    void timeoutTransitorioConReintento() throws Exception {
        String flowId = verifiedFlow("1000000001");
        updateEmail(flowId, unique("luis"), UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"));
    }

    @Test
    @DisplayName("CA-13: proveedor de identidad caído → 503 sin detalle interno")
    void proveedorDeIdentidadCaido() throws Exception {
        startFlow("1000000002", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.provider").value("IDENTITY_CAPABILITY"))
                .andExpect(jsonPath("$.detail", not(containsString("503 simulado"))));
    }

    @Test
    @DisplayName("CA-14: Messaging caído → UPDATED con notificación PENDING")
    void messagingCaido() throws Exception {
        String flowId = verifiedFlow("1000000003");
        updateEmail(flowId, unique("jorge"), UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationStatus").value("PENDING"));
    }

    @Test
    void flujoInexistente() throws Exception {
        verifyOtp(UUID.randomUUID().toString(), "123456")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOW_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ utilidades

    private ResultActions startFlow(String documentNumber, String correlationId) throws Exception {
        var request = post(FLOWS).contentType(MediaType.APPLICATION_JSON).header("User-Agent", SAFARI_17)
                .content("{\"documentType\":\"CC\",\"documentNumber\":\"" + documentNumber + "\"}");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        return mvc.perform(request);
    }

    private ResultActions verifyOtp(String flowId, String code) throws Exception {
        return postJson(FLOWS + "/" + flowId + "/otp/verify", "{\"code\":\"" + code + "\"}");
    }

    private ResultActions updateEmail(String flowId, String email, String key) throws Exception {
        return putEmail(flowId, "{\"newEmail\":\"" + email + "\",\"confirmEmail\":\"" + email + "\"}", key);
    }

    private ResultActions putEmail(String flowId, String body, String key) throws Exception {
        var request = put(FLOWS + "/" + flowId + "/email").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (flowCorrelationId != null) {
            request.header("X-Correlation-Id", flowCorrelationId);
        }
        return mvc.perform(request);
    }

    private ResultActions postJson(String path, String body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (flowCorrelationId != null) {
            request.header("X-Correlation-Id", flowCorrelationId);
        }
        return mvc.perform(request);
    }

    private String verifiedFlow(String documentNumber) throws Exception {
        String flowId = flowIdOf(startFlow(documentNumber, null));
        verifyOtp(flowId, "123456").andExpect(status().isOk());
        return flowId;
    }

    private static String flowIdOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.flowId");
    }

    /** El simulador conserva estado entre pruebas: correos únicos evitan EMAIL_SAME_AS_CURRENT. */
    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@dominio.com";
    }
}
