package com.missioncontrol.platform;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions into RFC 9457 {@code application/problem+json} responses.
 *
 * <p>Using {@link ProblemDetail} keeps error shapes consistent across every module and gives the
 * generated TypeScript client a single error type to model, instead of each module inventing its
 * own error envelope.
 *
 * <p>Errors detected inside the security filter chain never reach this class - they are written by
 * {@link ProblemAuthenticationEntryPoint} and {@link ProblemAccessDeniedHandler} instead. Both
 * paths build their bodies from the same helpers so the two are indistinguishable to a client.
 *
 * <p>Ordered last so that a more specific advice added later wins without anyone having to
 * remember why.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Any module's declared API error. One handler serves them all - see
     * {@link ApiProblemException}.
     *
     * <p>Logged at debug, not error: these are expected outcomes, and a stack trace per failed
     * login would drown the log. Nothing about the exception is logged beyond its type and detail,
     * neither of which carries a credential.
     */
    @ExceptionHandler(ApiProblemException.class)
    public ProblemDetail handleApiProblem(ApiProblemException ex) {
        log.debug("Request rejected: {} ({})", ex.getMessage(), ex.getStatus());
        return ex.toProblemDetail();
    }

    /**
     * An {@link AccessDeniedException} raised <em>inside</em> a handler method - by
     * {@code @PreAuthorize}, for instance.
     *
     * <p>This one is easy to miss. Such an exception is resolved by MVC before
     * {@code ExceptionTranslationFilter} ever sees it, so without this handler the catch-all below
     * would turn every role denial into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return SecurityProblems.forbidden();
    }

    /** As above, for an authentication failure surfacing on the MVC path. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return SecurityProblems.unauthenticated();
    }

    /**
     * A request to a path with no handler.
     *
     * <p>Without this the catch-all below would report a mistyped URL as a 500, which is both
     * wrong and actively misleading when debugging - it suggests the server broke rather than that
     * the caller asked for something that does not exist.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No such resource.");
        problem.setTitle("Not found");
        problem.setType(ProblemTypes.NOT_FOUND);
        return problem;
    }

    /**
     * A known path called with a method it does not support - a {@code DELETE} on a skill, for
     * instance, which feature 03 deliberately does not offer.
     *
     * <p>Same trap as the handler above: without it the catch-all turns a 405 into a 500, and a
     * client trying an endpoint that was never meant to exist is told the server broke.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "Method not supported for this resource.");
        problem.setTitle("Method not allowed");
        problem.setType(ProblemTypes.METHOD_NOT_ALLOWED);
        return problem;
    }

    /** Bean-validation failures on {@code @Valid @RequestBody} arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        problem.setType(ProblemTypes.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Bean-validation failures on path variables and request parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Validation failed");
        problem.setType(ProblemTypes.VALIDATION_FAILED);
        return problem;
    }

    /**
     * A path variable or request parameter that could not be converted to its declared type - a
     * malformed UUID in {@code /api/skills/&#123;id&#125;}, say, or a non-numeric page index.
     *
     * <p>Without this the catch-all below reports a client typo as a 500. The detail names the
     * offending parameter but never echoes the submitted value, which is caller-controlled and has
     * no business being reflected into a response body.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' is not a valid value.");
        problem.setTitle("Validation failed");
        problem.setType(ProblemTypes.VALIDATION_FAILED);
        return problem;
    }

    /**
     * A request body that is absent, empty or not valid JSON.
     *
     * <p>Without this the catch-all below turns a missing body into a 500, which reads as an
     * application fault for what is squarely a malformed request - the 400 row in the error table
     * every spec shares. It was reachable before feature 05 and simply never exercised: any
     * endpoint with a required {@code RequestBody} - creating a mission, adding a requirement -
     * answered 500 to an empty {@code POST}. Rejecting a mission without a comment is the first
     * case a spec named explicitly, which is how it surfaced.
     *
     * <p>The detail says nothing about what was wrong with the payload. Jackson's own message names
     * types and offsets from the caller's input, and reflecting that back is both noise and a small
     * disclosure.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
        problem.setTitle("Validation failed");
        problem.setType(ProblemTypes.VALIDATION_FAILED);
        return problem;
    }

    /**
     * Catch-all. The exception is logged in full, but the response deliberately carries no detail -
     * stack traces and internal messages are not the client's business.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        return problem;
    }
}
