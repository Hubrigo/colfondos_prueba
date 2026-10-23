package com.colfondos.contactupdate.api;

import java.time.OffsetDateTime;

public record UpdateEmailResponse(
        String transactionId,
        String status,
        String maskedEmail,
        OffsetDateTime updatedAt,
        NotificationStatus notificationStatus) {

    /** {@code PENDING}: Messaging falló tras los reintentos; la actualización se mantiene (E2 D-05). */
    public enum NotificationStatus { SENT, PENDING }
}
