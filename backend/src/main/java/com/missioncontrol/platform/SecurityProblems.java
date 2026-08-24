package com.missioncontrol.platform;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The two security problem responses, built in one place.
 *
 * <p>{@link #unauthenticated()} takes no argument on purpose. A missing token, a malformed one, an
 * expired one and one revoked by logout all produce the identical body. Distinguishing them would
 * tell an attacker which of those four states they are in - in particular it would confirm that a
 * token was once valid and has since been revoked, which is exactly the kind of thing the spec's
 * 'identical error either way' rule exists to prevent.
 */
final class SecurityProblems {

    private SecurityProblems() {
    }

    static ProblemDetail unauthenticated() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
        problem.setTitle("Unauthenticated");
        problem.setType(ProblemTypes.UNAUTHENTICATED);
        return problem;
    }

    static ProblemDetail forbidden() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Your role does not permit this action.");
        problem.setTitle("Forbidden");
        problem.setType(ProblemTypes.FORBIDDEN);
        return problem;
    }
}
