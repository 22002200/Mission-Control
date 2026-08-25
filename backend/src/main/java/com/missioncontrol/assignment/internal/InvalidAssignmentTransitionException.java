package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The action cannot be taken from where this assignment - or its mission - currently is.
 *
 * <p>Two causes, one URN, because they are the same thing to a client: the state it was looking at
 * is not the state the server holds. Both carry {@code currentStatus} as a property so a stale
 * screen can be told from a genuine mistake, which is exactly what
 * {@code InvalidMissionTransitionException} does for missions and for the same reason - seeing that
 * an offer you thought was open is now {@code WITHDRAWN} is a cue to refresh, not an error to show.
 *
 * <p>The mission variant is invariant A1: places may only be offered while a mission is
 * {@code APPROVED}. Not {@code ACTIVE} - a mission already flying is not taking on crew, and a seat
 * vacated after launch is dealt with by editing the plan, which sends the mission back to
 * {@code PLAN} under M5.
 */
class InvalidAssignmentTransitionException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:invalid-transition");

    private final String currentStatus;
    private final String attemptedTransition;

    private InvalidAssignmentTransitionException(String detail, String currentStatus,
                                                 String attemptedTransition) {
        super(HttpStatus.CONFLICT, TYPE, "Invalid transition", detail);
        this.currentStatus = currentStatus;
        this.attemptedTransition = attemptedTransition;
    }

    /** Invariant A7: the assignment has already been settled, or never could take this move. */
    static InvalidAssignmentTransitionException assignment(AssignmentStatus current,
                                                           AssignmentStatus attempted) {
        return new InvalidAssignmentTransitionException(
                "An assignment that is " + current + " cannot become " + attempted + ".",
                current.name(),
                attempted.name());
    }

    /** Invariant A1: the mission is not taking offers. */
    static InvalidAssignmentTransitionException mission(MissionStatus current) {
        return new InvalidAssignmentTransitionException(
                "Crew can only be offered a place while a mission is APPROVED, and this one is "
                        + current + ".",
                current.name(),
                MissionStatus.APPROVED.name());
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("currentStatus", currentStatus);
        problem.setProperty("attemptedTransition", attemptedTransition);
        return problem;
    }
}
