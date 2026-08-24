package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * A requirement named a skill that is unknown, retired, or in another organisation.
 *
 * <p>All three answer the same way, and the detail names no id. A distinct message for 'that skill
 * exists but not here' would confirm the existence of a row in a tenant the caller cannot read.
 *
 * <p>Retired skills are refused for new requirements but still render on old ones - that is
 * invariant S2, and it is why the lookup returns the active flag rather than filtering.
 */
class InvalidSkillException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:invalid-skill");

    InvalidSkillException() {
        super(HttpStatus.CONFLICT, TYPE, "Invalid skill",
                "A requirement can only name active skills from the organisation catalogue.");
    }
}
