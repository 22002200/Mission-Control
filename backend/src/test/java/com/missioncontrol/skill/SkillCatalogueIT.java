package com.missioncontrol.skill;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.missioncontrol.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Feature 03's read endpoints end to end: a real token, a real database, the seeded catalogues.
 *
 * <p>This is also where the acceptance criterion feature 02 could not reach lands - a user asking
 * for a resource in another organisation gets a 404, not a 403. See the scope note on
 * {@code TenantScopingIT}.
 */
class SkillCatalogueIT extends AbstractIntegrationTest {

    /** Orbital Dynamics has eight seeded skills, Helios Aerospace six. */
    private static final int ORG_A_SEEDED = 8;
    private static final int ORG_B_SEEDED = 6;

    private static final String EVA_IN_ORG_A = "a2000000-0000-0000-0000-000000000001";
    private static final String EVA_IN_ORG_B = "b2000000-0000-0000-0000-000000000001";

    private static final String PROBLEM_JSON = "application/problem+json";

    private MockHttpServletRequestBuilder asUser(
            MockHttpServletRequestBuilder request, String email) throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    @Test
    @DisplayName("The list is the caller's own organisation, sorted by name")
    void listReturnsTheCallersCatalogueSortedByName() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ORG_A_SEEDED))
                .andExpect(jsonPath("$.content.length()").value(ORG_A_SEEDED))
                .andExpect(jsonPath("$.content[0].name").value("Comms and Telemetry"))
                .andExpect(jsonPath("$.content[1].name").value("EVA Operations"))
                .andExpect(jsonPath("$.content[7].name").value("Robotics"));
    }

    @Test
    @DisplayName("BR-3: neither organisation can see the other's catalogue")
    void eachOrganisationSeesOnlyItsOwnCatalogue() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ORG_B_SEEDED))
                .andExpect(jsonPath("$.content[0].name").value("EVA Operations"))
                .andExpect(jsonPath("$.content[5].name").value("Solar Array Maintenance"));

        // Geology Sampling belongs to Orbital Dynamics alone.
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_B)
                        .param("search", "Geology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("An organisation id on the request cannot redirect the query")
    void theRequestCannotOverrideTheOrganisation() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A)
                        .param("organisationId", ORG_B_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ORG_A_SEEDED));
    }

    @Test
    @DisplayName("BR-4: every authenticated role may read the catalogue")
    void allThreeRolesCanRead() throws Exception {
        for (String email : new String[] {DIRECTOR_A, MISSION_LEAD_A, NEVER_LOGGED_OUT_CREW}) {
            mockMvc.perform(asUser(get("/api/skills"), email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(ORG_A_SEEDED));
        }
    }

    @Test
    void theCatalogueNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));

        mockMvc.perform(get("/api/skills/{id}", EVA_IN_ORG_A))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSearchTermMatchesTheNameCaseInsensitively() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("search", "eva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("EVA Operations"));

        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("search", "SYSTEMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Life Support Systems"))
                .andExpect(jsonPath("$.content[1].name").value("Propulsion Systems"));
    }

    @Test
    @DisplayName("A wildcard in the search term is searched for, not interpreted")
    void aWildcardInTheSearchTermMatchesNothing() throws Exception {
        // Were these passed through unescaped, both would return the entire catalogue.
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("search", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Every seeded skill is active, so the two active filters partition the catalogue")
    void theActiveFilterSelects() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ORG_A_SEEDED));

        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagingWalksTheCatalogue() throws Exception {
        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A)
                        .param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(ORG_A_SEEDED))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Comms and Telemetry"));

        mockMvc.perform(asUser(get("/api/skills"), DIRECTOR_A)
                        .param("page", "2").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].name").value("Robotics"));
    }

    @Test
    void getReturnsOneSkill() throws Exception {
        mockMvc.perform(asUser(get("/api/skills/{id}", EVA_IN_ORG_A), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVA_IN_ORG_A))
                .andExpect(jsonPath("$.name").value("EVA Operations"))
                .andExpect(jsonPath("$.category").value("Operations"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("BR-3: another organisation's skill is absent, not forbidden")
    void anotherOrganisationsSkillIsNotFound() throws Exception {
        // The row exists. What must not leak is that it exists.
        mockMvc.perform(asUser(get("/api/skills/{id}", EVA_IN_ORG_B), DIRECTOR_A))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));

        mockMvc.perform(asUser(get("/api/skills/{id}", EVA_IN_ORG_A), DIRECTOR_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("A skill that exists elsewhere is reported exactly like one that never existed")
    void aCrossTenantMissIsIndistinguishableFromAnUnknownId() throws Exception {
        JsonNode crossTenant = notFoundBody(EVA_IN_ORG_B);
        JsonNode neverExisted = notFoundBody("ffffffff-ffff-ffff-ffff-ffffffffffff");

        // `instance` is the path the caller just asked for, so it differs by construction and tells
        // them nothing they did not already know. Everything else must match to the character.
        org.assertj.core.api.Assertions.assertThat(crossTenant).isEqualTo(neverExisted);
    }

    /** The problem body for a failed fetch, with the request path removed. */
    private JsonNode notFoundBody(String id) throws Exception {
        JsonNode body = json(mockMvc.perform(asUser(get("/api/skills/{id}", id), DIRECTOR_A))
                .andExpect(status().isNotFound())
                .andReturn());
        return ((ObjectNode) body).without("instance");
    }

    @Test
    @DisplayName("The same name in both organisations is two rows with two ids")
    void bothOrganisationsHoldTheirOwnEvaOperations() throws Exception {
        mockMvc.perform(asUser(get("/api/skills/{id}", EVA_IN_ORG_A), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EVA Operations"));

        mockMvc.perform(asUser(get("/api/skills/{id}", EVA_IN_ORG_B), DIRECTOR_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EVA Operations"));
    }
}
