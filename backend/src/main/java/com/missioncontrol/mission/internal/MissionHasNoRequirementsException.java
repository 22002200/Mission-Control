package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * A mission cannot be submitted for approval with nothing to staff - invariant M12, BR-5.
 *
 * <p>Without this rule an empty mission is <em>vacuously</em> fully staffed under M11: there is no
 * requirement that is short, so nothing is missing, so it could be started the moment it was
 * approved. Feature 04 had to refuse the same case one step later, at {@code POST /start}, because
 * this check had nowhere to live yet; {@link MissionUnderstaffedException} is that later net. This
 * is the real fix, and it catches the mission before a director ever looks at it.
 */
class MissionHasNoRequirementsException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:mission-has-no-requirements");

    MissionHasNoRequirementsException() {
        super(HttpStatus.CONFLICT, TYPE, "Mission has no crew requirements",
                "A mission needs at least one crew requirement before it can be submitted for "
                        + "approval.");
    }
}
