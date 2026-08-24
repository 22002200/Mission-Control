package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * No such requirement on that mission.
 *
 * <p>Also what a requirement that exists but belongs to a different mission gets, so a caller
 * cannot use a mismatched pair of ids to confirm that a requirement id is real.
 */
class RequirementNotFoundException extends ApiProblemException {

    RequirementNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found",
                "No such crew requirement on this mission.");
    }
}
