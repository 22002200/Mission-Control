package com.missioncontrol.identity.internal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.platform.GlobalExceptionHandler;
import com.missioncontrol.shared.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the three auth endpoints.
 *
 * <p>Filters are off: this is about what the controller and the exception handler produce, not
 * about who is allowed through - that is {@code SecurityFilterChainApiTest}.
 *
 * <p>Every error case asserts the exact {@code type} URN and the problem+json content type,
 * because those are what the spec's error table promises and what the generated client models.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class AuthControllerApiTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant EXPIRES = Instant.parse("2026-03-01T17:00:00Z");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthenticationService authentication;
    @MockitoBean private CurrentUser currentUser;

    private static CurrentUserResponse director() {
        return new CurrentUserResponse(USER_ID, "Vera Lindholm",
                "vera.lindholm@orbitaldynamics.example", UserRole.DIRECTOR,
                ORG_ID, "Orbital Dynamics");
    }

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    void loginReturnsATokenAndTheUser() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenReturn(new LoginResponse("the-token", EXPIRES, director()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("vera.lindholm@orbitaldynamics.example", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("the-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-03-01T17:00:00Z"))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.fullName").value("Vera Lindholm"))
                .andExpect(jsonPath("$.user.organisationName").value("Orbital Dynamics"));
    }

    @Test
    void rolesTravelAsStringsNotTheStoredIntegers() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenReturn(new LoginResponse("t", EXPIRES, director()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("vera@x.example", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("DIRECTOR"));
    }

    @Test
    void loginPassesTheSuppliedCredentialsThrough() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenReturn(new LoginResponse("t", EXPIRES, director()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Vera@X.example", "Password123!")))
                .andExpect(status().isOk());

        verify(authentication).login("Vera@X.example", "Password123!");
    }

    @Test
    void aMissingEmailIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "Password123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void aBlankPasswordIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("vera@x.example", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void badCredentialsAreUnauthorised() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("vera@x.example", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-credentials"))
                .andExpect(jsonPath("$.title").value("Invalid credentials"));
    }

    @Test
    void aDisabledAccountIsForbidden() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenThrow(new AccountDisabledException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("oona@x.example", "Password123!")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:account-disabled"));
    }

    @Test
    void theLoginErrorBodyNeverEchoesTheSubmittedCredentials() throws Exception {
        when(authentication.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("vera@x.example", "hunter2")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("hunter2")
                .doesNotContain("vera@x.example");
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        when(currentUser.userId()).thenReturn(USER_ID);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authentication).logout(USER_ID);
    }

    @Test
    void meReturnsTheCallersOwnRecord() throws Exception {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(authentication.currentUser(eq(USER_ID))).thenReturn(director());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("vera.lindholm@orbitaldynamics.example"))
                .andExpect(jsonPath("$.role").value("DIRECTOR"))
                .andExpect(jsonPath("$.organisationId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.organisationName").value("Orbital Dynamics"));
    }

    @Test
    void meNeverExposesAPasswordHash() throws Exception {
        when(currentUser.userId()).thenReturn(USER_ID);
        when(authentication.currentUser(eq(USER_ID))).thenReturn(director());

        String body = mockMvc.perform(get("/api/auth/me"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$");
    }

    @Test
    void theUserIdComesFromTheTokenNotTheRequest() throws Exception {
        // FR-8 in miniature: /me takes no parameter, so there is nothing to tamper with. Asking
        // for someone else's record is not a request this API can express.
        UUID somebodyElse = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(currentUser.userId()).thenReturn(USER_ID);
        when(authentication.currentUser(eq(USER_ID))).thenReturn(director());

        mockMvc.perform(get("/api/auth/me").param("userId", somebodyElse.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));

        verify(authentication).currentUser(USER_ID);
    }
}
