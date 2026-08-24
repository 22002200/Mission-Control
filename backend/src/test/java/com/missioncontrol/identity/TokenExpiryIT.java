package com.missioncontrol.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.AuthenticatedUser;
import com.missioncontrol.platform.JwtProperties;
import com.missioncontrol.platform.JwtTokenIssuer;
import com.missioncontrol.shared.UserRole;
import com.missioncontrol.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * Requirement FR-3: a token stops working eight hours after it was issued.
 *
 * <p>Tested by minting with a shifted {@link Clock} rather than by waiting, and against the real
 * decoder the application uses - so this exercises the actual expiry policy, not a simulation of
 * it. The user is a real seeded one, so the revocation check passes and expiry is unambiguously
 * the thing being measured.
 */
class TokenExpiryIT extends AbstractIntegrationTest {

    // A user no other test logs out. These tokens are minted in the past, so a logout elsewhere
    // in the suite would revoke them and this class would fail for the wrong reason.
    private static final UUID SUBJECT = UUID.fromString(NEVER_LOGGED_OUT_CREW_ID);
    private static final UUID ORG_A_UUID = UUID.fromString(ORG_A_ID);

    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties jwtProperties;

    private String tokenIssuedHoursAgo(long hours) {
        JwtTokenIssuer issuer = new JwtTokenIssuer(
                jwtEncoder,
                jwtProperties,
                Clock.offset(Clock.systemUTC(), Duration.ofHours(-hours)));

        return issuer.issue(
                new AuthenticatedUser(SUBJECT, ORG_A_UUID, UserRole.CREW_MEMBER)).value();
    }

    @Test
    @DisplayName("A token past its 8-hour expiry is rejected")
    void anExpiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(tokenIssuedHoursAgo(9))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    @DisplayName("A token still inside its 8-hour window is accepted")
    void aTokenWithinItsLifetimeIsAccepted() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(tokenIssuedHoursAgo(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEVER_LOGGED_OUT_CREW));
    }

    @Test
    @DisplayName("An expired token is reported exactly like no token at all")
    void expiryDoesNotAnnounceItself() throws Exception {
        String expired = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(tokenIssuedHoursAgo(9))))
                .andReturn().getResponse().getContentAsString();

        String absent = mockMvc.perform(get("/api/auth/me"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(expired).isEqualTo(absent);
    }
}
