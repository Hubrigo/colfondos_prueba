package com.colfondos.contactupdate.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Enmascaramiento de PII para logs, auditoría y respuestas (doc. 08). */
public final class Masking {

    private Masking() {
    }

    /** {@code ana.perez@dominio.com} → {@code an***@dominio.com}. */
    public static String email(String email) {
        if (email == null || email.indexOf('@') < 1) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** {@code 1012345678} → {@code ******5678}. */
    public static String document(String number) {
        if (number == null || number.length() <= 4) {
            return "****";
        }
        return "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }

    /** {@code 3001234567} → {@code 300****567}. */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    /** Referencia no reversible del afiliado, para correlacionar sin exponer el documento. */
    public static String customerRef(String documentType, String documentNumber) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((documentType + ":" + documentNumber).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
