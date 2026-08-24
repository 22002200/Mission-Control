package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The same skill was listed twice on one requirement - invariant M10.
 *
 * <p>Caught before the insert so the caller gets this rather than a constraint violation surfacing
 * as a 500. The composite primary key is still there as the real guarantee; this is the readable
 * version of it.
 */
class DuplicateSkillException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:duplicate-skill");

    DuplicateSkillException() {
        super(HttpStatus.CONFLICT, TYPE, "Duplicate skill",
                "A crew requirement can list each skill only once.");
    }
}
