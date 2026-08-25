package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Crew requirements may only change while the mission is in {@code PLAN} - BR-10.
 *
 * <p>Distinct from an invalid transition, because nothing is being transitioned. Changing what
 * crew a mission needs after it has been approved would invalidate the approval without going
 * through the resubmission path that M5 defines for mission details, so the write is refused
 * outright rather than quietly sending the mission back to planning.
 */
class MissionNotEditableException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:mission-not-editable");

    private final MissionStatus currentStatus;

    MissionNotEditableException(MissionStatus currentStatus) {
        super(HttpStatus.CONFLICT, TYPE, "Mission not editable",
                "Crew requirements can only be changed while a mission is in PLAN, and this one "
                        + "is in " + currentStatus + ".");
        this.currentStatus = currentStatus;
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("currentStatus", currentStatus.name());
        return problem;
    }
}
