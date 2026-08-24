package com.missioncontrol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Logout, and the fact that it revokes every token rather than just the one presented.
 *
 * <p>Uses the mission-lead account so it cannot interfere with the director-based tests sharing
 * this context - logout is a write, and these tests run in the same database.
 */
class LogoutRevocationIT extends AbstractIntegrationTest {

    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("After logout, the token used to log out is rejected")
    void theTokenUsedToLogOutStopsWorking() throws Exception {
        String token = tokenFor(MISSION_LEAD_A);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    @DisplayName("After logout, a different token issued earlier is also rejected")
    void everyOutstandingTokenIsRevoked() throws Exception {
        // Two separate logins - as if the user were signed in on two devices.
        String first = tokenFor(CREW_A);
        String second = tokenFor(CREW_A);

        // If these were equal the test would prove nothing, and they would be equal if the token
        // carried only whole-second precision.
        assertThat(first).isNotEqualTo(second);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk());

        // Log out using the *second* token only.
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(second)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(second)))
                .andExpect(status().isUnauthorized());

        // The first token was never presented to logout, and must still be dead.
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    @DisplayName("Logging in again immediately after logout works")
    void aFreshLoginRightAfterLogoutIsAccepted() throws Exception {
        // The regression that a whole-second `iat` would cause. Logout stamps a microsecond
        // instant; a login milliseconds later must not look older than it and be rejected.
        String token = tokenFor(DIRECTOR_B);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        String freshToken = tokenFor(DIRECTOR_B);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DIRECTOR_B));
    }

    @Test
    @DisplayName("Logout is the only write: reading /me does not touch the user row")
    void readingDoesNotWrite() throws Exception {
        String email = "priya.raman@orbitaldynamics.example";
        String token = tokenFor(email);

        Timestamp before = jdbc().queryForObject(
                "SELECT updated_at FROM app_user WHERE lower(email) = lower(?)",
                Timestamp.class, email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }

        Timestamp after = jdbc().queryForObject(
                "SELECT updated_at FROM app_user WHERE lower(email) = lower(?)",
                Timestamp.class, email);

        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("Logout stamps tokens_valid_from on the user")
    void logoutIsRecordedInTheDatabase() throws Exception {
        String email = "sofia.mendes@heliosaero.example";

        Timestamp before = jdbc().queryForObject(
                "SELECT tokens_valid_from FROM app_user WHERE lower(email) = lower(?)",
                Timestamp.class, email);
        assertThat(before).isNull();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer(tokenFor(email))))
                .andExpect(status().isNoContent());

        Timestamp after = jdbc().queryForObject(
                "SELECT tokens_valid_from FROM app_user WHERE lower(email) = lower(?)",
                Timestamp.class, email);
        assertThat(after).isNotNull();
    }

    @Test
    @DisplayName("Logout without a token is rejected")
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }
}
