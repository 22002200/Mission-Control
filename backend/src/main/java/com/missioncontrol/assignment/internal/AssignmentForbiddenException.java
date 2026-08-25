package com.missioncontrol.assignment.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * The caller can see this assignment but is not the person entitled to do that to it - BR-6, BR-9.
 *
 * <p>403 rather than 404 precisely because they can see it. A crew member offered a place, the lead
 * who owns the mission and a director all have legitimate sight of the same row; the refusal is
 * about the verb, not about existence, so hiding the row would be a lie. Where the caller genuinely
 * should not know the row exists, {@code AssignmentNotFoundException} answers first.
 */
class AssignmentForbiddenException extends ApiProblemException {

    AssignmentForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden", detail);
    }

    /** BR-6. A mission lead cannot answer on a crew member's behalf, and neither can a director. */
    static AssignmentForbiddenException notTheCrewMember(String verb) {
        return new AssignmentForbiddenException(
                "Only the crew member offered this place can " + verb + " it.");
    }

    /**
     * BR-9, and narrower than invariant M6 allows on purpose.
     *
     * <p>A director may read every assignment on every mission in the organisation and withdraw
     * none of them. Their lever on a mission they disagree with is closing it - the same narrowing
     * {@code POST /replan} already makes, and for the same reason: this is the owning lead's plan.
     */
    static AssignmentForbiddenException notTheOwningLead(String verb) {
        return new AssignmentForbiddenException(
                "Only the mission lead who owns this mission can " + verb + ".");
    }
}
