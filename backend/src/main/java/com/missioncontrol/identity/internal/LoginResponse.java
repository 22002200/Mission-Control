package com.missioncontrol.identity.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A successful login.
 *
 * <p>There is no refresh token by design: when this one expires the user logs in again.
 *
 * @param token     the signed JWT, to be sent as {@code Authorization: Bearer <token>}
 * @param expiresAt UTC instant the token stops being accepted
 * @param user      the same body {@code GET /api/auth/me} returns
 */
@Schema(description = "An access token and the user it identifies.")
public record LoginResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String token,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CurrentUserResponse user) {
}
