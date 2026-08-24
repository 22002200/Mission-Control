package com.missioncontrol.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the advice does with exceptions that escape a controller.
 *
 * <p>The {@link AccessDeniedException} case is the one that matters. Such an exception raised
 * inside a handler method - by {@code @PreAuthorize}, say - is resolved by MVC before Spring
 * Security's {@code ExceptionTranslationFilter} ever sees it. Without a dedicated handler the
 * catch-all would turn every role denial into a 500, and nothing else in the suite would notice.
 */
class GlobalExceptionHandlerApiTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void accessDeniedFromAControllerBecomesForbiddenNotAServerError() throws Exception {
        mockMvc.perform(get("/throw/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    void authenticationFailureFromAControllerBecomesUnauthorised() throws Exception {
        mockMvc.perform(get("/throw/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:mission-control:unauthenticated"));
    }

    @Test
    void aModulesOwnApiErrorKeepsItsTypeAndStatus() throws Exception {
        mockMvc.perform(get("/throw/api-problem"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:example"))
                .andExpect(jsonPath("$.title").value("Example"))
                .andExpect(jsonPath("$.detail").value("Something specific went wrong."));
    }

    @Test
    void anUnexpectedFailureRevealsNothingAboutItself() throws Exception {
        mockMvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:mission-control:internal-error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("database password"))));
    }

    /** A stand-in for any module's declared error. */
    private static final class ExampleProblem extends ApiProblemException {
        private ExampleProblem() {
            super(HttpStatus.CONFLICT, URI.create("urn:mission-control:example"), "Example",
                    "Something specific went wrong.");
        }
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("Access is denied");
        }

        @GetMapping("/throw/authentication")
        String authentication() {
            throw new BadCredentialsException("nope");
        }

        @GetMapping("/throw/api-problem")
        String apiProblem() {
            throw new ExampleProblem();
        }

        @GetMapping("/throw/unexpected")
        String unexpected() {
            throw new IllegalStateException("connection refused using database password hunter2");
        }
    }
}
