package com.colfondos.contactupdate.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEmailRequest(
        @NotBlank(message = "Ingresa el nuevo correo")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        @Pattern(regexp = EMAIL_PATTERN, message = "Ingresa un correo válido")
        String newEmail,
        @NotBlank(message = "Confirma el nuevo correo")
        String confirmEmail) {

    /** RFC 5322 simplificado: parte local habitual y dominio con al menos un punto (rechaza {@code a@b}). */
    public static final String EMAIL_PATTERN =
            "^\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+\\s*$";

    @JsonIgnore
    @AssertTrue(message = "Los correos no coinciden")
    public boolean isConfirmationMatching() {
        return newEmail == null || confirmEmail == null
                || newEmail.trim().equalsIgnoreCase(confirmEmail.trim());
    }

    @Override
    public String toString() {
        return "UpdateEmailRequest[newEmail=***]";
    }
}
