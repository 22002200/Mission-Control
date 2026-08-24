package com.missioncontrol.platform;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Base class for an exception that already knows the HTTP problem it should become.
 *
 * <p>Exists so a domain module can define its own errors, with its own {@code type} URNs, without
 * needing its own {@code @RestControllerAdvice} and without {@code platform} having to learn what
 * those errors mean. {@link GlobalExceptionHandler} handles this one type; every module's errors
 * ride along.
 *
 * <p>The stack trace is suppressed. These are expected outcomes on a well-trodden path - a wrong
 * password is not an incident - and filling in a stack trace for each one is pure cost.
 */
public abstract class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;

    protected ApiProblemException(HttpStatus status, URI type, String title, String detail) {
        super(detail, null, false, false);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, getMessage());
        problem.setTitle(title);
        problem.setType(type);
        return problem;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
