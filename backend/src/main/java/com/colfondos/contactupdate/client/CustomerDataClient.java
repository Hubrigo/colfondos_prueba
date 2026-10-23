package com.colfondos.contactupdate.client;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Puerto hacia Customer Data API (doc. 06): consulta de perfil y actualización de contacto. */
public interface CustomerDataClient {

    record Customer(String customerId, String email, String mobilePhone, String status) {
    }

    record UpdateResult(String transactionId, OffsetDateTime updatedAt) {
    }

    Optional<Customer> findByDocument(String documentType, String documentNumber);

    /**
     * Actualiza el correo. Exige el token de validación de identidad (H-01) y es
     * idempotente por {@code idempotencyKey}, por eso el orquestador puede reintentarla.
     */
    UpdateResult updateEmail(String customerId, String newEmail, String verificationToken, String idempotencyKey);
}
