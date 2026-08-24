package com.missioncontrol.identity.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials presented at login.
 *
 * <p>Note the absence of {@code @Email}. Validating the format here would answer a 400 for
 * {@code 'notanemail'} and a 401 for a well-formed address that happens not to exist, which is a
 * small but real signal about what the system considers a plausible account. Everything that
 * fails to match a user should look the same, so the only checks are presence and a length bound
 * matching the column.
 *
 * @param email    matched case-insensitively
 * @param password never logged, never stored
 */
@Schema(description = "Email and password.")
public record LoginRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "vera.lindholm@orbitaldynamics.example")
        @NotBlank(message = "must not be blank")
        @Size(max = 320, message = "must be at most 320 characters")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Password123!")
        @NotBlank(message = "must not be blank")
        String password) {
}
