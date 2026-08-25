package com.missioncontrol.assignment.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * This crew member already holds an open place on this mission - invariant A5.
 *
 * <p>Only offered and accepted count. Somebody who declined, or who was withdrawn, may be asked
 * again: the plan may have changed, and a refusal once is not a refusal forever.
 *
 * <p>Raised from two places, which is deliberate rather than duplication. The service checks it so
 * the common case gets this sentence, and the partial unique index behind it catches the race two
 * concurrent offers would otherwise win together. A check without the index is a suggestion; an
 * index without the check produces a constraint-violation stack trace instead of an explanation.
 */
class DuplicateAssignmentException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:duplicate-assignment");

    DuplicateAssignmentException() {
        super(HttpStatus.CONFLICT, TYPE, "Already assigned",
                "This crew member already holds a place on this mission.");
    }
}
