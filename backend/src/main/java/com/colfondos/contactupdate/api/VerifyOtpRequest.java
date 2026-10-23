package com.colfondos.contactupdate.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Ingresa el código")
        @Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos")
        String code) {

    /** El código nunca se imprime, ni siquiera por accidente en un log del record. */
    @Override
    public String toString() {
        return "VerifyOtpRequest[code=******]";
    }
}
