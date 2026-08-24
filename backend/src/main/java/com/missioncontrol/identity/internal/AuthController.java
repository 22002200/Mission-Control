package com.missioncontrol.identity.internal;

import com.missioncontrol.platform.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout, and the caller's own record.
 *
 * <p>Internal to the module: HTTP is a delivery detail of whoever owns the data.
 *
 * <p>Method names matter here. springdoc derives {@code operationId} from them, and that becomes
 * the function name in the committed TypeScript client - so renaming a method silently renames part
 * of the frontend's API.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Exchanging credentials for a token, and revoking it.")
class AuthController {

    private final AuthenticationService authentication;
    private final CurrentUser currentUser;

    AuthController(AuthenticationService authentication, CurrentUser currentUser) {
        this.authentication = authentication;
        this.currentUser = currentUser;
    }

    /**
     * The one endpoint reachable without a token, so it opts out of the document-wide bearer
     * requirement - otherwise the generated client would attach a stale token when signing in.
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Log in",
            description = "Exchanges an email and password for a signed token valid for 8 hours. "
                    + "An unknown email and a wrong password return the same error, deliberately.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "Missing or malformed body",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Account is not active",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authentication.login(request.email(), request.password());
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out",
            description = "Revokes every token issued to the caller, not only the one presented.")
    @ApiResponse(responseCode = "204", description = "Tokens revoked")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> logout() {
        authentication.logout(currentUser.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "The caller's identity and role.")
    @ApiResponse(responseCode = "200", description = "The authenticated user")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    CurrentUserResponse currentUser() {
        return authentication.currentUser(currentUser.userId());
    }
}
