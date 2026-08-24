package com.missioncontrol.skill.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Resolving skill ids for modules that reference the catalogue without owning it.
 *
 * <p>The first published type in this module. It exists because {@code mission} stores a
 * {@code skillId} on every required skill and has to do two things with it: reject one that is
 * unknown or retired, and print its name. Neither is possible from an id alone, and reaching into
 * {@code skill.internal} is what {@code ModularityTests} exists to stop.
 *
 * <p>Bulk by design. A crew requirement lists several skills and a mission lists several
 * requirements, so a lookup per skill would be an N+1 across a single page - which NFR-1 of
 * feature 04 forbids. There is no single-id variant: offering one would make the N+1 the path of
 * least resistance.
 *
 * <p>Takes the organisation explicitly rather than reading {@code CurrentUser}, so the tenant a
 * result is scoped to is visible at the call site. Ids belonging to another organisation are
 * absent from the returned map rather than raising - to the caller they are simply unknown, which
 * is invariant T2.
 */
public interface SkillCatalogue {

    /**
     * Looks up several skills at once.
     *
     * @return the skills that exist in that organisation, keyed by id. Unknown ids are absent, so
     *         the caller decides whether a miss is an error - it is a 409 for a mission
     *         requirement and might not be elsewhere. An empty input yields an empty map without
     *         touching the database.
     */
    Map<UUID, SkillSummary> findByIds(Collection<UUID> skillIds, UUID organisationId);
}
