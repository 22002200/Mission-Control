package com.missioncontrol.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.shared.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requirement FR-9: a valid token with the wrong role is rejected.
 *
 * <p>Feature 02 ships no role-restricted endpoint of its own - the first is
 * {@code POST /api/skills} in feature 03 - so this exercises the mechanism against a controller
 * declared here. That makes it a test of the wiring: that {@code @EnableMethodSecurity} is on,
 * that the {@code role} claim becomes a {@code ROLE_} authority, and that a denial comes back as
 * the problem type the spec names rather than as an empty 403 or a 500.
 */
@WebMvcTest(controllers = RoleEnforcementApiTest.DirectorOnlyController.class)
@Import({RoleEnforcementApiTest.DirectorOnlyController.class, SecurityConfig.class,
        JwtSecurityConfig.class, ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class, ProblemResponseWriter.class,
        SecurityContextCurrentUser.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RoleEnforcementApiTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtDecoder jwtDecoder;

    private void tokenFor(String value, UserRole role) {
        Jwt jwt = Jwt.withTokenValue(value)
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.ORGANISATION_ID, UUID.randomUUID().toString())
                .claim(JwtClaims.ROLE, role.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        org.mockito.Mockito.when(jwtDecoder.decode(value)).thenReturn(jwt);
    }

    @Test
    void theRightRoleIsAllowed() throws Exception {
        tokenFor("director", UserRole.DIRECTOR);

        mockMvc.perform(get("/api/test/director-only")
                        .header("Authorization", "Bearer director"))
                .andExpect(status().isOk());
    }

    @Test
    void aValidTokenWithTheWrongRoleIsForbidden() throws Exception {
        tokenFor("crew", UserRole.CREW_MEMBER);

        mockMvc.perform(get("/api/test/director-only").header("Authorization", "Bearer crew"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    void aMissionLeadIsAlsoForbidden() throws Exception {
        tokenFor("lead", UserRole.MISSION_LEAD);

        mockMvc.perform(get("/api/test/director-only").header("Authorization", "Bearer lead"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    void noTokenIsUnauthenticatedRatherThanForbidden() throws Exception {
        // The distinction matters: 401 means "identify yourself", 403 means "you did, and it is
        // not enough". Collapsing them would make the API confusing to use and to debug.
        mockMvc.perform(get("/api/test/director-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @RestController
    static class DirectorOnlyController {

        @GetMapping("/api/test/director-only")
        @PreAuthorize("hasRole('DIRECTOR')")
        String directorOnly() {
            return "ok";
        }
    }
}
