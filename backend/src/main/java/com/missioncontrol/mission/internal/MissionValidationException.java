package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * A request that is well formed but says something impossible.
 *
 * <p>For the rules that relate two fields and so cannot be a field annotation - a mission that
 * ends before it starts, invariant M1 - and for a close reason that contradicts the mission's own
 * history. Both are 400s of the same shape bean validation produces, so a client has one error
 * type to handle rather than two.
 */
class MissionValidationException extends ApiProblemException {

    MissionValidationException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED, "Validation failed", detail);
    }
}
