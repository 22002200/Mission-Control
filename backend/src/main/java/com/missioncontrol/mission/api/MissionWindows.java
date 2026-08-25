package com.missioncontrol.mission.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Mission dates, names and capacity, for a module that commits people to them.
 *
 * <p>Published alongside {@link MissionPlans} rather than folded into it, because the two answer
 * different kinds of question and merging them would blur the one that matters.
 * {@code MissionPlans} answers <em>may this caller staff this mission</em>, reading the caller from
 * the security context and refusing with 404 or 403. This interface answers <em>what are these
 * missions</em>, takes the organisation as a parameter, and applies no permission check at all -
 * the same contract shape {@code UserDirectory} and {@code SkillCatalogue} already use.
 *
 * <p><strong>That is not a hole.</strong> Feature 07's crew-facing commands are called by a crew
 * member, who fails {@code MissionAccess.requireCanModify} by design; their right to act comes from
 * the assignment they hold - BR-6 - which is {@code assignment}'s rule and not this module's.
 * Routing those through a permission check written for mission leads would either refuse them
 * outright or force this module to learn what an assignment is.
 *
 * <p>Every read is scoped to the organisation passed in. Missions outside it are absent from the
 * result rather than reported, which is invariant T2 and is also what stops a caller confirming
 * that another tenant's mission id is real.
 */
public interface MissionWindows {

    /**
     * Several missions at once.
     *
     * <p>Bulk only, and there is no single-mission variant on purpose. A page of a crew member's
     * assignments spans many missions, and a per-row lookup is the N+1 feature 07's NFR-4 rules
     * out. The one place a single mission is genuinely needed is a command, and that one takes a
     * lock - see {@link #lockForUpdate}.
     *
     * @return those that exist in that organisation, keyed by id; unknown ids are absent. An empty
     *         input yields an empty map without touching the database.
     */
    Map<UUID, MissionWindow> findByIds(Collection<UUID> missionIds, UUID organisationId);

    /**
     * Several crew requirements at once, with the seats each one calls for.
     *
     * <p>Serves two callers with one query: printing a requirement's title beside an assignment,
     * and checking invariant A2's cap before an offer or an acceptance.
     *
     * @return those that exist in that organisation, keyed by requirement id; unknown ids are
     *         absent. An empty input yields an empty map without touching the database.
     */
    Map<UUID, RequirementSeat> findRequirements(Collection<UUID> requirementIds,
                                                UUID organisationId);

    /**
     * One mission, with a write lock held on its row for the rest of the transaction.
     *
     * <p><strong>This is the serialisation point for staffing.</strong> The mission row is already
     * the single point every command that changes a mission locks - see
     * {@code MissionRepository.lockByIdAndOrganisationId} - and staffing commands join that
     * discipline rather than inventing a second one. Two crew members racing for the last seat on a
     * requirement serialise here, and so does a close racing an acceptance: the loser reads what
     * the winner committed instead of overwriting it.
     *
     * <p>Callers must take this <strong>before</strong> any other load of the same mission in the
     * transaction. Hibernate's first-level cache will otherwise hand back an instance read before
     * the lock, which is the whole failure the lock exists to prevent.
     *
     * @throws RuntimeException an {@code ApiProblemException} carrying 404 when no such mission
     *         exists in that organisation - absent and another tenant's are deliberately
     *         indistinguishable.
     */
    MissionWindow lockForUpdate(UUID missionId, UUID organisationId);
}
