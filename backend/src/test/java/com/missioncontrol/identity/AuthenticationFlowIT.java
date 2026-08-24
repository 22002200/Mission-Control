package com.missioncontrol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The acceptance criteria of {@code docs/features/02-authentication.md}, end to end against a real
 * database and the real seed data.
 */
class AuthenticationFlowIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("A seeded user can log in and receives a token")
    void seededUserCanLogIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DIRECTOR_A, SEED_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(DIRECTOR_A))
                .andExpect(jsonPath("$.user.fullName").value("Vera Lindholm"))
                .andExpect(jsonPath("$.user.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.user.organisationId").value(ORG_A_ID))
                .andExpect(jsonPath("$.user.organisationName").value("Orbital Dynamics"))
                .andReturn();

        // FR-3: eight hours, not the PT15M the configuration used to carry.
        Instant expiresAt = Instant.parse(json(result).get("expiresAt").asText());
        Instant expected = Instant.now().plus(Duration.ofHours(8));
        assertThat(expiresAt).isBetween(
                expected.minus(Duration.ofMinutes(2)), expected.plus(Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("Email is matched case-insensitively")
    void emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DIRECTOR_A.toUpperCase(), SEED_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(DIRECTOR_A));
    }

    @Test
    @DisplayName("A wrong password and an unknown email return the identical 401")
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = attemptLogin(DIRECTOR_A, "not-the-password");
        MvcResult unknownEmail = attemptLogin("nobody@orbitaldynamics.example", SEED_PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);

        // Byte-for-byte identical: anything less makes login an account-enumeration oracle.
        assertThat(unknownEmail.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());

        assertThat(json(wrongPassword).get("type").asText())
                .isEqualTo("urn:mission-control:invalid-credentials");
    }

    @Test
    @DisplayName("A disabled user cannot log in")
    void disabledUserCannotLogIn() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DISABLED_USER, SEED_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:account-disabled"));
    }

    @Test
    @DisplayName("A disabled user with a wrong password looks like any other bad login")
    void disabledUserWithWrongPasswordIsNotDistinguishable() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DISABLED_USER, "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-credentials"));
    }

    @Test
    @DisplayName("A missing field is a validation failure, not a credentials failure")
    void missingFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("/api/auth/me returns the caller's own record")
    void meReturnsTheCallersRecord() throws Exception {
        String token = tokenFor(DIRECTOR_A);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DIRECTOR_A_ID))
                .andExpect(jsonPath("$.email").value(DIRECTOR_A))
                .andExpect(jsonPath("$.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.organisationName").value("Orbital Dynamics"));
    }

    @Test
    @DisplayName("Every role can authenticate and carries its own role claim")
    void eachRoleAuthenticatesWithItsOwnRole() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor(MISSION_LEAD_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MISSION_LEAD"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor(CREW_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CREW_MEMBER"));
    }

    @Test
    @DisplayName("Any /api/** call without a token returns 401")
    void apiRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));

        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    @DisplayName("A garbage or foreign-signed token is rejected")
    void unusableTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));

        // Correctly structured, signed with a different key.
        String foreign = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhMTAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEifQ."
                + "Ym9ndXNzaWduYXR1cmV0aGF0d2lsbG5ldmVydmVyaWZ5";
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login is reachable without a token, and health is too")
    void publicEndpointsStayPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(DIRECTOR_A, SEED_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("The token response never contains a password hash")
    void nothingLeaksAHash() throws Exception {
        MvcResult result = attemptLogin(DIRECTOR_A, SEED_PASSWORD);

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("$2a$")
                .doesNotContain("passwordHash")
                .doesNotContain(SEED_PASSWORD);
    }

    @Test
    @DisplayName("An unknown path under /api returns 404, not 500")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/auth/does-not-exist")
                        .header("Authorization", bearer(tokenFor(DIRECTOR_A))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }
}
