package com.colfondos.contactupdate.service;

import com.colfondos.contactupdate.api.UpdateEmailResponse;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Estado de un flujo de actualización, guardado en el servidor (E2 D-04). El token de
 * verificación de identidad vive aquí y nunca viaja al navegador.
 *
 * <p>Transiciones: {@code OTP_SENT → VERIFIED → COMPLETED}, o {@code OTP_SENT → BLOCKED}.
 * Los métodos que mutan se invocan bajo {@code synchronized (flow)} desde el servicio.
 */
public class ContactFlow {

    public enum Status { OTP_SENT, VERIFIED, COMPLETED, BLOCKED }

    private final String flowId;
    private final String customerRef;
    private final String browser;
    private final OffsetDateTime expiresAt;

    /** {@code null} en un flujo señuelo: documento inexistente o afiliado inactivo (CA-03). */
    private String customerId;
    private String currentEmail;
    private Status status = Status.OTP_SENT;
    private String challengeId;
    private OffsetDateTime otpExpiresAt;
    private OffsetDateTime resendAvailableAt;
    private int resendCount;
    private int decoyFailedAttempts;
    private String verificationToken;
    private OffsetDateTime verifiedUntil;
    private String transactionId;
    private final Map<String, UpdateEmailResponse> idempotentResults = new HashMap<>();

    public ContactFlow(String flowId, String customerRef, String browser, OffsetDateTime expiresAt) {
        this.flowId = flowId;
        this.customerRef = customerRef;
        this.browser = browser;
        this.expiresAt = expiresAt;
    }

    void attachCustomer(String customerId, String currentEmail) {
        this.customerId = customerId;
        this.currentEmail = currentEmail;
    }

    void otpIssued(String challengeId, OffsetDateTime otpExpiresAt, OffsetDateTime resendAvailableAt) {
        this.challengeId = challengeId;
        this.otpExpiresAt = otpExpiresAt;
        this.resendAvailableAt = resendAvailableAt;
    }

    void otpResent(String challengeId, OffsetDateTime otpExpiresAt, OffsetDateTime resendAvailableAt) {
        otpIssued(challengeId, otpExpiresAt, resendAvailableAt);
        resendCount++;
    }

    int registerDecoyFailure() {
        return ++decoyFailedAttempts;
    }

    void verified(String verificationToken, OffsetDateTime verifiedUntil) {
        this.status = Status.VERIFIED;
        this.verificationToken = verificationToken;
        this.verifiedUntil = verifiedUntil;
    }

    void block() {
        this.status = Status.BLOCKED;
        this.verificationToken = null;
    }

    /** El token es de un solo uso: se descarta al completar la actualización. */
    void completed(String transactionId) {
        this.status = Status.COMPLETED;
        this.transactionId = transactionId;
        this.verificationToken = null;
    }

    void rememberResult(String idempotencyKey, UpdateEmailResponse response) {
        idempotentResults.put(idempotencyKey, response);
    }

    Optional<UpdateEmailResponse> resultFor(String idempotencyKey) {
        return Optional.ofNullable(idempotentResults.get(idempotencyKey));
    }

    public boolean isDecoy() {
        return customerId == null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt);
    }

    public String flowId() {
        return flowId;
    }

    public String customerRef() {
        return customerRef;
    }

    public String browser() {
        return browser;
    }

    public String customerId() {
        return customerId;
    }

    public String currentEmail() {
        return currentEmail;
    }

    public Status status() {
        return status;
    }

    public String challengeId() {
        return challengeId;
    }

    public OffsetDateTime otpExpiresAt() {
        return otpExpiresAt;
    }

    public OffsetDateTime resendAvailableAt() {
        return resendAvailableAt;
    }

    public int resendCount() {
        return resendCount;
    }

    public String verificationToken() {
        return verificationToken;
    }

    public OffsetDateTime verifiedUntil() {
        return verifiedUntil;
    }

    public String transactionId() {
        return transactionId;
    }
}
