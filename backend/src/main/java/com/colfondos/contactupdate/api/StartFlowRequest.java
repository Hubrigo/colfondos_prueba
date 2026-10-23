package com.colfondos.contactupdate.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record StartFlowRequest(
        @NotNull(message = "Selecciona el tipo de documento") DocumentType documentType,
        @NotBlank(message = "Ingresa el número de documento")
        @Pattern(regexp = "^[A-Za-z0-9]{5,15}$", message = "Debe tener entre 5 y 15 caracteres, sin espacios ni símbolos")
        String documentNumber) {

    public enum DocumentType { CC, CE, PA, TI }

    /** Solo el pasaporte admite letras; los demás documentos son numéricos. */
    @JsonIgnore
    @AssertTrue(message = "Ingresa solo números")
    public boolean isNumberValidForType() {
        if (documentType == null || documentNumber == null || documentType == DocumentType.PA) {
            return true;
        }
        return documentNumber.chars().allMatch(Character::isDigit);
    }
}
