package com.missioncontrol.crew.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An organisation's crew roster, for modules that rank or report on crew without owning them.
 *
 * <p>The first published type in this module. It exists because matching has to score every crew
 * member in the organisation against a requirement, which means it needs all of them and all of
 * their ratings, and reaching into {@code crew.internal} is what {@code ModularityTests} exists to
 * stop.
 *
 * <p>{@link #rosterOf} is whole-roster by design and has no single-crew-member variant. Matching
 * ranks a population rather than looking someone up, and offering a one-at-a-time method would make
 * the N+1 the path of least resistance - the same reasoning {@code SkillCatalogue} and
 * {@code UserDirectory} both record. Feature 06's NFR-2 forbids a per-candidate query outright.
 *
 * <p>The two id lookups added for feature 07 are a different question and answer it with ids
 * rather than profiles. {@code assignment} stores a {@code crewMemberId} but authenticates a
 * {@code userId}, and it needs to cross between the two in both directions - to know whose
 * assignment it is holding, and to resolve a name through {@code identity}. Handing back a
 * {@link CrewProfile} for either would fetch a map of skill ratings that neither caller reads.
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

    /**
     * The crew profile belonging to one account, if that account has one in this organisation.
     *
     * <p>A single-id method, unlike everything else here, and it earns the exception: this answers
     * 'who am I' for the caller of a request, once, not 'what is everyone worth'. There is no loop
     * it could sit inside.
     *
     * <p>Empty means the user is not a crew member of that organisation - a director, a mission
     * lead, or somebody else's account entirely. Callers read all three the same way, which is
     * what stops this becoming a way to probe another tenant's accounts.
     */
    Optional<UUID> crewMemberIdOf(UUID userId, UUID organisationId);

    /**
     * The accounts behind several crew profiles.
     *
     * <p>Bulk, because the caller is about to ask {@code identity} for their names and a name per
     * row is the N+1 feature 07's NFR-4 forbids. Ids only: this module holds no names and cannot
     * resolve one, which is the point of {@code userId} being a bare reference in the first place.
     *
     * @return user ids keyed by crew member id, for those that exist in that organisation; unknown
     *         ids are absent. An empty input yields an empty map without touching the database.
     */
    Map<UUID, UUID> userIdsByCrewMemberId(Collection<UUID> crewMemberIds, UUID organisationId);
}
