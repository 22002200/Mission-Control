package com.missioncontrol.platform;

import java.time.Instant;

/**
 * A freshly minted access token and the instants that bound its life.
 *
 * @param value     the signed, encoded JWT
 * @param issuedAt  when it was minted, at full precision
 * @param expiresAt when it stops being accepted
 */
public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {
}
