package com.missioncontrol.matching.internal;

import com.missioncontrol.crew.api.CrewDirectory;
import com.missioncontrol.crew.api.CrewProfile;
import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.mission.api.MissionPlan;
import com.missioncontrol.mission.api.MissionPlans;
import com.missioncontrol.mission.api.RequirementPlan;
import com.missioncontrol.skill.api.SkillCatalogue;
import com.missioncontrol.skill.api.SkillSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The engine: everything feature 06 does, between the controller and the modules it reads.
 *
 * <p><strong>One snapshot per request.</strong> The roster, the ratings, the availability sets and
 * the load counts are fetched once and every requirement is scored against that same picture -
 * NFR-2 and NFR-7. Doing it per requirement would be an N+1 in the number of staffing lines, and
 * worse: two requirements could be scored against different data if an assignment landed between
 * them, which would make the cross-requirement de-duplication in BR-10 incoherent.
 *
 * <p>The query budget is fixed regardless of how many requirements a mission has or how many crew
 * an organisation has - the mission and its requirements, the median mission length, the staffing
 * counts, the roster, the four assignment reads and two bulk name lookups. Everything after that is
 * arithmetic in memory: roughly requirements times candidates times skills, which at NFR-3's five
 * hundred crew is tens of thousands of operations and nowhere near the two-second budget.
 *
 * <p>{@code Transactional(readOnly = true)} is a statement of intent as much as a setting. NFR-4
 * says this writes nothing, and a read-only transaction makes an accidental write fail rather than
 * succeed quietly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class CrewMatchingService {

    private final MissionPlans missions;
    private final CrewDirectory crew;
    private final SkillCatalogue skills;
    private final UserDirectory users;
    private final CrewLoad crewLoad;
    private final LoadWindow loadWindow;
    private final CandidateScorer scorer;
    private final Clock clock;

    /**
     * Match All - FR-9. A candidate for every open seat on the mission, nobody twice.
     *
     * <p>Not the per-requirement call in a loop. De-duplication needs every requirement's candidate
     * list at once, because deciding who gets a contested candidate means knowing which of the two
     * lines has fewer alternatives - BR-10.
     */
    @Transactional(readOnly = true)
    MissionMatchResponse matchAll(UUID missionId) {
        MissionPlan plan = missions.forStaffing(missionId);
        Snapshot snapshot = snapshotFor(plan);

        List<RankedRequirement> ranked = plan.requirements().stream()
                .map(requirement -> new RankedRequirement(requirement, rank(requirement, snapshot)))
                .toList();

        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(ranked);

        log.debug("Match all on mission {} drafted {} of {} eligible across {} requirements",
                missionId,
                drafted.values().stream().mapToInt(List::size).sum(),
                ranked.stream().mapToInt(RankedRequirement::eligibleCount).sum(),
                ranked.size());

        // Mission order, not allocation order. Most-constrained-first is how the draft was decided,
        // not how a mission lead expects to read it back.
        return new MissionMatchResponse(plan.id(), ranked.stream()
                .map(entry -> {
                    List<ScoredCandidate> candidates =
                            drafted.getOrDefault(entry.requirement().id(), List.of());
                    return describe(entry.requirement(), candidates,
                            entry.eligibleCount() - candidates.size(), snapshot);
                })
                .toList());
    }

    /**
     * Match and Rematch - FR-1, FR-12, FR-13. One requirement, minus whoever the caller has already
     * seen or drafted.
     *
     * <p>Exclusions are applied before the limit, so a rematch returns a full list whenever enough
     * candidates remain rather than a short one with holes where the excluded people were.
     *
     * @param exclude crew member ids to leave out. Unknown ids, ids from another organisation and
     *                ids that are already ineligible are ignored rather than rejected - a client
     *                holding a stale draft should get a shorter list, not an error.
     */
    @Transactional(readOnly = true)
    RequirementMatchResponse matchRequirement(UUID missionId, UUID requirementId, int limit,
                                              Set<UUID> exclude) {
        MissionPlan plan = missions.forStaffing(missionId);
        RequirementPlan requirement = plan.requirement(requirementId)
                .orElseThrow(RequirementNotOnMissionException::new);

        Snapshot snapshot = snapshotFor(plan);

        List<ScoredCandidate> eligible = rank(requirement, snapshot).stream()
                .filter(candidate -> !exclude.contains(candidate.crewMemberId()))
                .toList();

        List<ScoredCandidate> shown = eligible.stream().limit(limit).toList();

        return describe(requirement, shown, eligible.size() - shown.size(), snapshot);
    }

    /**
     * Everything the scoring needs, loaded once.
     *
     * <p>One ordering constraint: the recency cutoff is derived from the organisation's median
     * mission length, so the tempo read has to precede the load counts. Nothing else here depends
     * on anything else here.
     */
    private Snapshot snapshotFor(MissionPlan plan) {
        UUID organisationId = plan.organisationId();

        Set<UUID> unavailable =
                crewLoad.unavailableBetween(organisationId, plan.startsAt(), plan.endsAt());
        Set<UUID> alreadyOnMission = crewLoad.alreadyOnMission(plan.id());

        // BR-3 and BR-4, applied to the whole roster once. Both are mission-wide facts rather than
        // per-requirement ones, so filtering here keeps them out of the scoring loop entirely.
        List<CrewProfile> available = crew.rosterOf(organisationId).stream()
                .filter(profile -> !unavailable.contains(profile.crewMemberId()))
                .filter(profile -> !alreadyOnMission.contains(profile.crewMemberId()))
                .toList();

        List<UUID> crewMemberIds = available.stream().map(CrewProfile::crewMemberId).toList();
        Instant cutoff = loadWindow.recencyCutoff(organisationId, clock.instant());

        return new Snapshot(
                available,
                crewLoad.completedMissionCounts(organisationId, crewMemberIds),
                crewLoad.recentAssignmentCounts(organisationId, crewMemberIds, cutoff),
                skills.findByIds(skillIdsOn(plan), organisationId),
                namesByCrewMember(available, organisationId));
    }

    /**
     * Display names, resolved once and keyed by crew member rather than by user.
     *
     * <p>Keyed that way because every later lookup has a crew member in hand and would otherwise
     * have to search the roster for the matching user id - a scan per candidate per requirement,
     * which is exactly the shape NFR-2 exists to prevent even when it costs no query.
     */
    private Map<UUID, String> namesByCrewMember(List<CrewProfile> roster, UUID organisationId) {
        Map<UUID, UserSummary> byUserId = users.findByIds(
                roster.stream().map(CrewProfile::userId).toList(), organisationId);

        Map<UUID, String> names = new HashMap<>();
        roster.forEach(profile -> {
            UserSummary user = byUserId.get(profile.userId());
            // A crew profile whose user is missing should be impossible - invariant C1 pairs them -
            // but a null name would reach the client as a blank row that says nothing about why.
            names.put(profile.crewMemberId(),
                    user == null ? "Unknown crew member" : user.fullName());
        });
        return names;
    }

    private static Collection<UUID> skillIdsOn(MissionPlan plan) {
        // A set because two requirements routinely ask for the same skill, and resolving one name
        // twice is wasted work in a lookup this module makes on every request.
        Set<UUID> ids = new LinkedHashSet<>();
        plan.requirements().forEach(requirement ->
                requirement.skills().forEach(skill -> ids.add(skill.skillId())));
        return ids;
    }

    /** Every eligible candidate for one requirement, best first - BR-2 filters, BR-9 orders. */
    private List<ScoredCandidate> rank(RequirementPlan requirement, Snapshot snapshot) {
        return snapshot.available().stream()
                .map(profile -> scorer.score(requirement, profile,
                        snapshot.completedMissions().getOrDefault(profile.crewMemberId(), 0),
                        snapshot.recentAssignments().getOrDefault(profile.crewMemberId(), 0)))
                .flatMap(Optional::stream)
                .sorted(ScoredCandidate.ranking())
                .toList();
    }

    private RequirementMatchResponse describe(RequirementPlan requirement,
                                              List<ScoredCandidate> candidates, int remaining,
                                              Snapshot snapshot) {
        return new RequirementMatchResponse(
                requirement.id(),
                requirement.title(),
                requirement.requiredCount(),
                requirement.acceptedCount(),
                requirement.offeredCount(),
                requirement.openSeats(),
                remaining,
                candidates.stream().map(candidate -> describe(candidate, snapshot)).toList());
    }

    private CandidateResponse describe(ScoredCandidate candidate, Snapshot snapshot) {
        List<CandidateSkillResponse> skills = candidate.skills().stream()
                .map(contribution -> describe(contribution, snapshot))
                .toList();

        // Shortfalls are derived from the same list rather than assembled separately, so the two
        // cannot disagree about what falling short means.
        List<CandidateSkillResponse> shortfalls = candidate.skills().stream()
                .filter(SkillContribution::isShortfall)
                .map(contribution -> describe(contribution, snapshot))
                .toList();

        return new CandidateResponse(
                candidate.crewMemberId(),
                snapshot.crewNames().getOrDefault(candidate.crewMemberId(), "Unknown crew member"),
                candidate.score(),
                new CandidateBreakdown(candidate.skillScore(), candidate.experienceBonus(),
                        candidate.completedMissions(), candidate.loadPenalty(),
                        candidate.recentAssignments()),
                skills,
                shortfalls);
    }

    private CandidateSkillResponse describe(SkillContribution contribution, Snapshot snapshot) {
        SkillSummary skill = snapshot.skillNames().get(contribution.skillId());
        return new CandidateSkillResponse(
                contribution.skillId(),
                // A retired skill is still named - invariant S2 keeps it readable precisely so that
                // an existing requirement does not turn into a blank row. Absent from the catalogue
                // entirely should not happen and is not worth failing the whole response over.
                skill == null ? "Unknown skill" : skill.name(),
                contribution.required(),
                contribution.actual(),
                contribution.mandatory(),
                contribution.weight(),
                contribution.contribution());
    }

    /**
     * The per-request picture: who is available, what they have done, and how to name things.
     *
     * <p>A record rather than fields on the service, so the fact that every requirement is scored
     * against one immutable snapshot is visible in the code and not merely intended.
     */
    private record Snapshot(List<CrewProfile> available,
                            Map<UUID, Integer> completedMissions,
                            Map<UUID, Integer> recentAssignments,
                            Map<UUID, SkillSummary> skillNames,
                            Map<UUID, String> crewNames) {
    }
}
