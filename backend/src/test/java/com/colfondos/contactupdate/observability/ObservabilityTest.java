package com.colfondos.contactupdate.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** Enmascaramiento de PII (doc. 08) y detección de navegador para indicadores (doc. 11). */
class ObservabilityTest {

    @ParameterizedTest
    @CsvSource({
            "ana.perez@dominio.com, an***@dominio.com",
            "ab@dominio.com,        a***@dominio.com",
            "a@dominio.com,         a***@dominio.com",
            "sin-arroba,            ***",
            "@dominio.com,          ***"})
    void enmascaraCorreo(String email, String expected) {
        assertThat(Masking.email(email)).isEqualTo(expected);
    }

    @Test
    void enmascaraDocumentoYCelular() {
        assertThat(Masking.document("1012345678")).isEqualTo("******5678");
        assertThat(Masking.document("123")).isEqualTo("****");
        assertThat(Masking.phone("3001234567")).isEqualTo("300****567");
    }

    @Test
    @DisplayName("La referencia del afiliado es estable, no reversible y distingue el tipo de documento")
    void referenciaDeAfiliado() {
        String ref = Masking.customerRef("CC", "1012345678");

        assertThat(ref).hasSize(24).isEqualTo(Masking.customerRef("CC", "1012345678"))
                .doesNotContain("1012345678")
                .isNotEqualTo(Masking.customerRef("CE", "1012345678"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 | Safari 17",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 | Chrome 141",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0 | Edge 141",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/141.0 Mobile/15E148 Safari/604.1 | Chrome 141",
            "curl/8.4.0 | Other"})
    @DisplayName("Edge y Chrome no se confunden con Safari aunque su User-Agent diga 'Safari'")
    void detectaNavegador(String userAgent, String expected) {
        assertThat(BrowserDetector.detect(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void userAgentAusente(String userAgent) {
        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Unknown");
    }
}
