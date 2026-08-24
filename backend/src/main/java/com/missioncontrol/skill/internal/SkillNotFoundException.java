package com.missioncontrol.skill.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * No skill with that id in the caller's organisation.
 *
 * <p>One exception for both causes - it does not exist, or it belongs to another organisation -
 * and no id in the detail. Distinguishing them would let anyone probe for which ids are real in a
 * tenant they cannot see, which is the leak BR-3 exists to prevent.
 */
class SkillNotFoundException extends ApiProblemException {

    SkillNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", "No such skill.");
    }
}
