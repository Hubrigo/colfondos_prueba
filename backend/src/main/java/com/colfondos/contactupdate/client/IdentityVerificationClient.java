package com.colfondos.contactupdate.client;

import java.time.OffsetDateTime;

/**
 * Puerto hacia Identity Capability API (doc. 06): genera, valida y registra el
 * código de verificación. El orquestador nunca conoce ni registra el código generado.
 */
public interface IdentityVerificationClient {

    enum Channel { SMS, EMAIL }

    enum Result { VALID, INVALID, EXPIRED, BLOCKED }

    record OtpChallenge(String challengeId, OffsetDateTime expiresAt) {
    }

    /** {@code verificationToken} solo viene cuando {@code result == VALID}. */
    record OtpValidation(Result result, int remainingAttempts, String verificationToken) {
    }

    OtpChallenge generateOtp(String customerId, Channel channel);

    OtpValidation validateOtp(String challengeId, String code);
}
