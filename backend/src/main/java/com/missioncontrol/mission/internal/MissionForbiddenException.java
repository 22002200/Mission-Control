package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * The caller can see this mission but may not do this to it - invariant M6.
 *
 * <p>A 403 rather than a 404, and that is a deliberate distinction from
 * {@link MissionNotFoundException}. Nothing is leaked by admitting the mission exists, because the
 * caller is in the organisation that owns it and can already read it; the only thing being refused
 * is the write.
 */
class MissionForbiddenException extends ApiProblemException {

    MissionForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Forbidden", detail);
    }
}
