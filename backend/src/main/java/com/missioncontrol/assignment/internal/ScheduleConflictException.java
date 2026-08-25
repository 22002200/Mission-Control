package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Accepting this would put the crew member on two missions at once - invariants A3 and A4.
 *
 * <p><strong>The error this feature exists to produce.</strong> Offers reserve nobody, so two leads
 * may quite legitimately ask the same person for clashing dates and neither offer is wrong. The
 * second acceptance is what has to fail, and it fails as a normal outcome rather than as a fault -
 * which is why the detail names the mission already committed to instead of saying no.
 *
 * <p>The conflicting mission's name and dates ride along as properties. A crew member seeing
 * 'you are already on Zenith Station Run, 30 Jul to 5 Dec' can decide what to do; one seeing
 * 'schedule conflict' has to go and look. The mission named is one they hold an accepted assignment
 * on, so this discloses nothing they cannot already see.
 */
class ScheduleConflictException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:schedule-conflict");

    private final MissionWindow conflicting;

    ScheduleConflictException(MissionWindow conflicting) {
        super(HttpStatus.CONFLICT, TYPE, "Schedule conflict",
                "This clashes with " + conflicting.name() + ", which you have already accepted.");
        this.conflicting = conflicting;
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("conflictingMissionId", conflicting.id().toString());
        problem.setProperty("conflictingMissionName", conflicting.name());
        problem.setProperty("conflictingStartsAt", conflicting.startsAt().toString());
        problem.setProperty("conflictingEndsAt", conflicting.endsAt().toString());
        return problem;
    }
}
