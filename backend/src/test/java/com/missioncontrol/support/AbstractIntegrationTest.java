package com.missioncontrol.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base for tests that run against the whole application and a real database.
 *
 * <p>Every subclass shares one Spring context and therefore one container - see
 * {@link TestcontainersConfiguration}. The seed data from the Liquibase changelogs is present, so
 * these tests log in as the same users a developer would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    /** Every seeded user shares this password. See {@code db/changelog/modules/identity}. */
    protected static final String SEED_PASSWORD = "Password123!";

    protected static final String DIRECTOR_A = "vera.lindholm@orbitaldynamics.example";
    protected static final String MISSION_LEAD_A = "marcus.reyes@orbitaldynamics.example";
    protected static final String CREW_A = "ada.kowalski@orbitaldynamics.example";
    protected static final String DIRECTOR_B = "tomas.eriksen@heliosaero.example";
    protected static final String DISABLED_USER = "oona.halvorsen@orbitaldynamics.example";

    /**
     * Accounts reserved for tests that must never have their tokens revoked by someone else.
     *
     * <p>All integration tests share one database, and logout is a write on the user row. A test
     * holding a token while another test logs the same user out will fail intermittently and for a
     * reason that is genuinely hard to see. Splitting the roster by test class is cruder than
     * per-test isolation but far easier to keep true.
     */
    protected static final String NEVER_LOGGED_OUT_CREW = "bruno.sato@orbitaldynamics.example";
    protected static final String NEVER_LOGGED_OUT_CREW_ID =
            "a1000000-0000-0000-0000-000000000005";
    protected static final String LOG_TEST_CREW = "chen.ibarra@orbitaldynamics.example";

    protected static final String ORG_A_ID = "a0000000-0000-0000-0000-000000000001";
    protected static final String ORG_B_ID = "b0000000-0000-0000-0000-000000000001";
    protected static final String DIRECTOR_A_ID = "a1000000-0000-0000-0000-000000000001";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    protected String loginBody(String email, String password) {
        return """
                {"email": %s, "password": %s}
                """.formatted(quote(email), quote(password));
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Logs in and returns the raw response, whatever its status. */
    protected MvcResult attemptLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andReturn();
    }

    /** Logs in, expecting success, and returns the token. */
    protected String tokenFor(String email) throws Exception {
        MvcResult result = attemptLogin(email, SEED_PASSWORD);

        int status = result.getResponse().getStatus();
        if (status != 200) {
            throw new AssertionError(
                    "Expected login to succeed for " + email + " but got " + status + ": "
                            + result.getResponse().getContentAsString());
        }
        return json(result).get("token").asText();
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
