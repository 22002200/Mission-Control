package com.missioncontrol.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Turns 'you are not authenticated' into a 401 {@code application/problem+json} body.
 *
 * <p>The exception is deliberately ignored: every cause collapses to one response. See
 * {@link SecurityProblems#unauthenticated()}.
 */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter writer;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(response, SecurityProblems.unauthenticated());
    }
}
