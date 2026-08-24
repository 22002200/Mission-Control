package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The mission is not in a state this action can be taken from - invariant M3.
 *
 * <p>Carries the current status and the attempted one as extra properties, so a client can tell a
 * stale view from a genuine mistake: seeing that a mission it thought was in {@code PLAN} is now
 * {@code PENDING_APPROVAL} is a cue to refresh, not to show an error. Feature 05 relies on the
 * same two properties.
 */
class InvalidMissionTransitionException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:invalid-transition");

    private final MissionStatus currentStatus;
    private final MissionStatus attemptedTransition;

    InvalidMissionTransitionException(MissionStatus currentStatus,
                                      MissionStatus attemptedTransition) {
        super(HttpStatus.CONFLICT, TYPE, "Invalid transition",
                "A mission in " + currentStatus + " cannot move to " + attemptedTransition + ".");
        this.currentStatus = currentStatus;
        this.attemptedTransition = attemptedTransition;
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("currentStatus", currentStatus.name());
        problem.setProperty("attemptedTransition", attemptedTransition.name());
        return problem;
    }
}
