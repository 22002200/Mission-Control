package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * No mission with that id that the caller is allowed to see.
 *
 * <p>One exception for three different causes - it does not exist, it belongs to another
 * organisation, or it is outside the caller's visibility - and no id in the detail. Telling them
 * apart would let anyone probe for which missions are real in a tenant they cannot see, which is
 * the leak invariant T2 exists to prevent.
 */
class MissionNotFoundException extends ApiProblemException {

    MissionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", "No such mission.");
    }
}
