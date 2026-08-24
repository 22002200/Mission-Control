package com.missioncontrol.crew.api;

import java.util.List;
import java.util.UUID;

/**
 * An organisation's crew roster, for modules that rank or report on crew without owning them.
 *
 * <p>The first published type in this module. It exists because matching has to score every crew
 * member in the organisation against a requirement, which means it needs all of them and all of
 * their ratings, and reaching into {@code crew.internal} is what {@code ModularityTests} exists to
 * stop.
 *
 * <p>Whole-roster by design, and there is no single-crew-member variant. Matching ranks a
 * population rather than looking someone up, and offering a one-at-a-time method would make the
 * N+1 the path of least resistance - the same reasoning {@code SkillCatalogue} and
 * {@code UserDirectory} both record. Feature 06's NFR-2 forbids a per-candidate query outright.
 *
 * <p>Takes the organisation explicitly rather than reading {@code CurrentUser}, so the tenant a
 * result is scoped to is visible at the call site. Crew in another organisation are not in the
 * result - invariant T2 - which is also what stops a caller using this to discover that another
 * tenant has anyone at all.
 */
public interface CrewDirectory {

    /**
     * Every crew member in one organisation, with their skill ratings already attached.
     *
     * <p>One query, ratings included. An organisation with no crew yields an empty list rather
     * than raising: an organisation that has not hired anyone is a valid state, not an error.
     *
     * @return the roster in no particular order. Callers that need one impose it - matching sorts
     *         by score and breaks ties on the crew member id, which is its own concern and not
     *         something a directory should presume.
     */
    List<CrewProfile> rosterOf(UUID organisationId);
}
