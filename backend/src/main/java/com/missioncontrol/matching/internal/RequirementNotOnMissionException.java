package com.missioncontrol.matching.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * No such requirement on that mission.
 *
 * <p>Also what a requirement that exists but belongs to a different mission gets, so a caller
 * cannot pair a mission they can see with a guessed requirement id to confirm the id is real. The
 * mission module's own not-found error takes the same line for the same reason; this is a separate
 * type only because that one is internal to {@code mission} and must stay there.
 */
class RequirementNotOnMissionException extends ApiProblemException {

    RequirementNotOnMissionException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found",
                "No such crew requirement on this mission.");
    }
}
