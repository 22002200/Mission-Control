package com.missioncontrol.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.shared.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Who the filter chain lets through, and what it says when it does not.
 *
 * <p>Requirement FR-7 in test form: every {@code /api/**} endpoint except login needs a token. The
 * subject under test is {@link SecurityConfig} itself, so filters stay on and the decoder is the
 * only thing mocked.
 */
@WebMvcTest(controllers = SystemInfoController.class)
@Import({SecurityConfig.class, JwtSecurityConfig.class, ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class, ProblemResponseWriter.class,
        SecurityContextCurrentUser.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class SecurityFilterChainApiTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    /**
     * Replaces the real decoder, so these tests do not need a signing secret and can simulate any
     * verification failure directly.
     */
    @MockitoBean private JwtDecoder jwtDecoder;

    private static Jwt validToken() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.ORGANISATION_ID, UUID.randomUUID().toString())
                .claim(JwtClaims.ROLE, UserRole.DIRECTOR.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void anApiCallWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void aValidTokenGetsThrough() throws Exception {
        org.mockito.Mockito.when(jwtDecoder.decode("good")).thenReturn(validToken());

        mockMvc.perform(get("/api/system/info").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());
    }

    @Test
    void aRejectedTokenIsIndistinguishableFromNoTokenAtAll() throws Exception {
        // The anti-leak assertion. A token that was valid and has since been revoked must not be
        // reported any differently from one that never existed - otherwise the response confirms
        // that an account exists and was recently logged out.
        org.mockito.Mockito.when(jwtDecoder.decode("revoked"))
                .thenThrow(new BadJwtException("The token is no longer valid."));

        MvcResult noToken = mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult revoked = mockMvc.perform(
                        get("/api/system/info").header("Authorization", "Bearer revoked"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(revoked.getResponse().getContentAsString())
                .isEqualTo(noToken.getResponse().getContentAsString());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        org.mockito.Mockito.when(jwtDecoder.decode("expired"))
                .thenThrow(new BadJwtException("Jwt expired"));

        mockMvc.perform(get("/api/system/info").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    void aMalformedAuthorizationHeaderIsRejected() throws Exception {
        org.mockito.Mockito.when(jwtDecoder.decode("not.a.jwt"))
                .thenThrow(new BadJwtException("Malformed token"));

        mockMvc.perform(get("/api/system/info").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    void theErrorBodyNeverEchoesTheRejectedToken() throws Exception {
        org.mockito.Mockito.when(jwtDecoder.decode("secret-token-value"))
                .thenThrow(new BadJwtException("bad"));

        String body = mockMvc.perform(get("/api/system/info")
                        .header("Authorization", "Bearer secret-token-value"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-token-value");
    }

    @Test
    void loginIsReachableWithoutAToken() throws Exception {
        // AuthController is not in this slice, so a 404 means the request reached the dispatcher
        // and found no handler. That is the success condition: a 401 would mean the filter chain
        // turned it away before it got there.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthAndApiDocsAreReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
