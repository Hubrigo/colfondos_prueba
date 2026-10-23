package com.colfondos.contactupdate.client.sim;

import com.colfondos.contactupdate.client.IdentityVerificationClient;
import com.colfondos.contactupdate.client.Provider;
import com.colfondos.contactupdate.client.ProviderException;
import com.colfondos.contactupdate.config.ContactUpdateProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Simula Identity Capability API: es el dueño del código, de los intentos y del
 * token de verificación, como lo sería el proveedor real.
 */
@Profile("sim")
@Component
public class SimulatedIdentityClient implements IdentityVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedIdentityClient.class);

    private static final class Challenge {
        final String customerId;
        final String code;
        final OffsetDateTime expiresAt;
        int failedAttempts;

        Challenge(String customerId, String code, OffsetDateTime expiresAt) {
            this.customerId = customerId;
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    private record Grant(String customerId, OffsetDateTime expiresAt) {
    }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Grant> tokens = new ConcurrentHashMap<>();
    private final ContactUpdateProperties.Otp otp;
    private final SimulationProperties simulation;
    private final Clock clock;

    public SimulatedIdentityClient(ContactUpdateProperties properties, SimulationProperties simulation, Clock clock) {
        this.otp = properties.otp();
        this.simulation = simulation;
        this.clock = clock;
    }

    @Override
    public OtpChallenge generateOtp(String customerId, Channel channel) {
        if (SimulationScenarios.IDENTITY_DOWN.equals(customerId)) {
            throw new ProviderException(Provider.IDENTITY_CAPABILITY, ProviderException.Kind.UNAVAILABLE,
                    "HTTP 503 simulado");
        }
        String challengeId = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(otp.ttl());
        challenges.put(challengeId, new Challenge(customerId, simulation.fixedOtp(), expiresAt));
        // Nunca se registra el código, ni siquiera en simulación.
        log.atInfo().addKeyValue("challengeId", challengeId).addKeyValue("channel", channel)
                .log("OTP simulado enviado");
        return new OtpChallenge(challengeId, expiresAt);
    }

    @Override
    public synchronized OtpValidation validateOtp(String challengeId, String code) {
        Challenge challenge = challengeId == null ? null : challenges.get(challengeId);
        if (challenge == null || OffsetDateTime.now(clock).isAfter(challenge.expiresAt)) {
            return new OtpValidation(Result.EXPIRED, 0, null);
        }
        if (challenge.failedAttempts >= otp.maxAttempts()) {
            return new OtpValidation(Result.BLOCKED, 0, null);
        }
        if (MessageDigest.isEqual(challenge.code.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            challenges.remove(challengeId);
            String token = UUID.randomUUID().toString();
            tokens.put(token, new Grant(challenge.customerId, OffsetDateTime.now(clock).plus(otp.ttl())));
            return new OtpValidation(Result.VALID, otp.maxAttempts() - challenge.failedAttempts, token);
        }
        challenge.failedAttempts++;
        return new OtpValidation(Result.INVALID, otp.maxAttempts() - challenge.failedAttempts, null);
    }

    /** Lo usa Customer Data simulado: el token es de un solo uso y está ligado al afiliado. */
    boolean consumeToken(String customerId, String token) {
        Grant grant = token == null ? null : tokens.remove(token);
        return grant != null && grant.customerId().equals(customerId)
                && !OffsetDateTime.now(clock).isAfter(grant.expiresAt());
    }
}
