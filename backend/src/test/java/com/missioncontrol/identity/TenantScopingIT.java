package com.missioncontrol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.JwtClaims;
import com.missioncontrol.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The tenancy primitive: the organisation a request is scoped to comes from the token and nowhere
 * else. Invariants T1 and T2.
 *
 * <p><strong>Scope, stated plainly.</strong> The acceptance criterion "a user from organisation A
 * requesting a resource in organisation B receives 404, not 403" cannot be tested here. Feature 02
 * ships three endpoints and none of them takes a resource id, so there is no cross-tenant fetch to
 * attempt - a 404 and a 403 are indistinguishable when there is no lookup to miss. That criterion
 * is discharged by {@code GET /api/skills/&#123;id&#125;} in feature 03.
 *
 * <p>What can be proved now is the foundation it rests on: two organisations' tokens carry
 * different organisation ids, each user sees only their own, and nothing in a request can change
 * which organisation the server believes the caller belongs to.
 */
class TenantScopingIT extends AbstractIntegrationTest {

    @Autowired private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Each organisation's users get their own organisation on the token")
    void tokensCarryTheirOwnOrganisation() throws Exception {
        Jwt tokenA = jwtDecoder.decode(tokenFor(DIRECTOR_A));
        Jwt tokenB = jwtDecoder.decode(tokenFor(DIRECTOR_B));

        assertThat(tokenA.getClaimAsString(JwtClaims.ORGANISATION_ID)).isEqualTo(ORG_A_ID);
        assertThat(tokenB.getClaimAsString(JwtClaims.ORGANISATION_ID)).isEqualTo(ORG_B_ID);
        assertThat(tokenA.getClaimAsString(JwtClaims.ORGANISATION_ID))
                .isNotEqualTo(tokenB.getClaimAsString(JwtClaims.ORGANISATION_ID));
    }

    @Test
    @DisplayName("A user only ever sees their own organisation")
    void eachUserSeesOnlyTheirOwnOrganisation() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor(DIRECTOR_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisationId").value(ORG_A_ID))
                .andExpect(jsonPath("$.organisationName").value("Orbital Dynamics"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor(DIRECTOR_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisationId").value(ORG_B_ID))
                .andExpect(jsonPath("$.organisationName").value("Helios Aerospace"));
    }

    @Test
    @DisplayName("An organisation id supplied on the request is ignored")
    void theRequestCannotOverrideTheOrganisation() throws Exception {
        // FR-8: no endpoint reads an organisation from a body, path or query parameter. Supplying
        // one has to be inert rather than merely undocumented, or it is an invitation to tamper.
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(tokenFor(DIRECTOR_A)))
                        .param("organisationId", ORG_B_ID)
                        .param("organisation", ORG_B_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisationId").value(ORG_A_ID));
    }

    @Test
    @DisplayName("A token names exactly one role, and it is the user's own")
    void roleComesFromTheUserRecord() throws Exception {
        assertThat(jwtDecoder.decode(tokenFor(DIRECTOR_A)).getClaimAsString(JwtClaims.ROLE))
                .isEqualTo("DIRECTOR");
        assertThat(jwtDecoder.decode(tokenFor(MISSION_LEAD_A)).getClaimAsString(JwtClaims.ROLE))
                .isEqualTo("MISSION_LEAD");
        assertThat(jwtDecoder.decode(tokenFor(CREW_A)).getClaimAsString(JwtClaims.ROLE))
                .isEqualTo("CREW_MEMBER");
    }
}
