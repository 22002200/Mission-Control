package com.missioncontrol.identity.internal;

import com.missioncontrol.shared.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Who the caller is.
 *
 * <p>Every field is {@code REQUIRED} so the generated TypeScript properties are non-optional -
 * the same reasoning as {@code SystemInfo}. The role travels as its name; the integer code it is
 * stored as never appears on the wire.
 */
@Schema(description = "The authenticated user's identity and role.")
public record CurrentUserResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Vera Lindholm")
        String fullName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "vera.lindholm@orbitaldynamics.example")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "DIRECTOR")
        UserRole role,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID organisationId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Orbital Dynamics")
        String organisationName) {
}
