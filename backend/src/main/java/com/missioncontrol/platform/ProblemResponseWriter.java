package com.missioncontrol.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes a {@link ProblemDetail} straight onto the servlet response.
 *
 * <p>Needed because Spring Security's entry point and access-denied handler run inside the filter
 * chain, before any {@code @RestControllerAdvice} exists to translate an exception. Without this
 * the same logical error would come back in two different shapes depending on where it was
 * detected, and the generated TypeScript client models exactly one error type.
 *
 * <p>The {@link ObjectMapper} is injected rather than constructed. Boot's instance has
 * {@code ProblemDetailJacksonMixin} registered, which is what flattens the detail's extra
 * properties up to the top level; a hand-rolled mapper would silently emit a different document
 * from the one the MVC path produces.
 */
@Component
class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
