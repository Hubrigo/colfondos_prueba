package com.colfondos.contactupdate.api;

import java.time.OffsetDateTime;

public record VerifyOtpResponse(boolean verified, OffsetDateTime verifiedUntil) {
}
