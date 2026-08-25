package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionClosedEvent;
import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.platform.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning a mission, and moving it through the parts of its lifecycle that need no director.
 *
 * <p>The organisation comes from {@link CurrentUser} on every call and from nowhere else - no
 * method here takes one as an argument, so a controller has no way to pass the wrong one. That is
 * invariant T1 made structural rather than remembered.
 *
 * <p>Responses are built by {@link MissionDetailAssembler}, which owns the three bulk lookups
 * every mission read needs. Feature 05's approval commands answer with the same detail, and one
 * assembler shared between them is what stops a second, per-row copy appearing.
 *
 * <p><strong>Every command here loads through {@link MissionLoader#visibleForUpdate}</strong>, not
 * just the approval ones next door. Dirty checking writes {@code set status = ?} with no status
 * predicate, so a close that read a stale status would block on the winner's lock and then
 * overwrite it. The lock only works if nobody opts out of it.
 */
@Service
class MissionService {

    /**
     * No status filter means every state.
     *
     * <p>Spelled out as a full set rather than as an absent clause, because JPQL has no way to
     * skip an {@code in} and a filter that silently does nothing is worse than an explicit list.
     */
    private static final Set<MissionStatus> ALL_STATUSES = EnumSet.allOf(MissionStatus.class);

    private final MissionRepository missions;
    private final MissionLoader loader;
    private final MissionAccess access;
    private final MissionStaffing staffing;
    private final MissionApprovals approvals;
    private final MissionDetailAssembler assembler;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MissionService(MissionRepository missions,
                   MissionLoader loader,
                   MissionAccess access,
                   MissionStaffing staffing,
                   MissionApprovals approvals,
                   MissionDetailAssembler assembler,
                   CurrentUser currentUser,
                   ApplicationEventPublisher events,
                   Clock clock) {
        this.missions = missions;
        this.loader = loader;
        this.access = access;
        this.staffing = staffing;
        this.approvals = approvals;
        this.assembler = assembler;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    MissionPage list(Collection<MissionStatus> statuses, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Collection<MissionStatus> wanted =
                statuses == null || statuses.isEmpty() ? ALL_STATUSES : statuses;
        String namePattern = namePattern(search);
        UUID organisationId = currentUser.organisationId();

        Page<MissionEntity> found = switch (currentUser.role()) {
            case DIRECTOR -> missions.findForOrganisation(
                    organisationId, null, wanted, namePattern, pageable);
            case MISSION_LEAD -> missions.findForOrganisation(
                    organisationId, currentUser.userId(), wanted, namePattern, pageable);
            case CREW_MEMBER -> assignedPage(organisationId, wanted, namePattern, pageable);
        };

        return assembler.page(found);
    }

    @Transactional(readOnly = true)
    MissionResponse get(UUID id) {
        return assembler.detail(loader.visibleDetail(id));
    }

    @Transactional
    MissionResponse create(CreateMissionRequest request) {
        requireOrderedDates(request.startsAt(), request.endsAt());

        Instant now = clock.instant();
        MissionEntity mission = MissionEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(currentUser.organisationId())
                .name(request.name().strip())
                .description(blankToNull(request.description()))
                .status(MissionStatus.PLAN)
                // The owner is the caller, never a field on the request. That is what makes
                // invariant M2 structural: the endpoint is MISSION_LEAD only, so the owner is a
                // mission lead by construction and a director can never end up owning a mission.
                .missionLeadId(currentUser.userId())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .createdBy(currentUser.userId())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return assembler.detail(missions.save(mission));
    }

    @Transactional
    MissionResponse update(UUID id, UpdateMissionRequest request) {
        MissionEntity mission = loader.visibleForUpdate(id);
        access.requireCanModify(mission);
        requireNotClosed(mission);

        requireOrderedDates(
                request.startsAt() == null ? mission.getStartsAt() : request.startsAt(),
                request.endsAt() == null ? mission.getEndsAt() : request.endsAt());

        // Reverting an APPROVED or ACTIVE mission to PLAN happens inside the entity - M5.
        mission.updateDetails(
                request.name() == null ? null : request.name().strip(),
                request.description(),
                request.startsAt(),
                request.endsAt(),
                clock.instant());

        return assembler.detail(mission);
    }

    @Transactional
    MissionResponse start(UUID id) {
        MissionEntity mission = loader.visibleForUpdate(id);
        access.requireCanModify(mission);
        requireTransition(mission, MissionStatus.ACTIVE);
        requireFullyStaffed(mission);

        mission.start(clock.instant());
        return assembler.detail(mission);
    }

    @Transactional
    MissionResponse close(UUID id, CloseMissionRequest request) {
        MissionEntity mission = loader.visibleForUpdate(id);
        access.requireCanModify(mission);
        requireTransition(mission, MissionStatus.CLOSED);

        Instant now = clock.instant();
        // Before the status moves, while it still says whether a cycle can be open. Closing a
        // mission that was awaiting a decision settles that cycle as CANCELLED rather than leaving
        // it PENDING for a mission nobody will ever decide - and holding M8's unique index for a
        // resubmission that can never come.
        approvals.cancelOpen(mission, currentUser.userId(), request.comment(), now);

        mission.close(closeReasonFor(mission, request.closeReason()), request.comment(), now);

        // Announced rather than acted on. Closing a mission has to withdraw its outstanding offers
        // - feature 07's FR-8 - and that is a write into assignment, which this module must never
        // depend on. Published inside the transaction and consumed synchronously, so the close and
        // the withdrawals commit together or not at all. See MissionClosedEvent.
        events.publishEvent(new MissionClosedEvent(
                mission.getId(), mission.getOrganisationId(), now));

        return assembler.detail(mission);
    }

    private Page<MissionEntity> assignedPage(UUID organisationId,
                                             Collection<MissionStatus> statuses,
                                             String namePattern,
                                             Pageable pageable) {
        Set<UUID> assigned = staffing.missionIdsAssignedTo(currentUser.userId(), organisationId);
        if (assigned.isEmpty()) {
            // An empty in clause is not valid SQL, and there is nothing to ask the database for.
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return missions.findAssigned(organisationId, assigned, statuses, namePattern, pageable);
    }

    /**
     * Invariant M11, checked as a precondition of starting rather than as a standing rule.
     *
     * <p>Crew withdrawing from a mission that is already running does not send it backwards, so
     * this is deliberately not asserted anywhere else.
     *
     * <p>The error lists every short requirement rather than only the first. A mission lead
     * looking at eight of them needs to know which two to chase.
     *
     * <p>A mission with no requirements is refused as well, so that this and the
     * {@code fullyStaffed} flag on the response can never disagree. M11 read literally is
     * vacuously satisfied by an empty mission - data-model.md says as much - and M12 closes that
     * at submission time in feature 05. Refusing it here too costs one branch and means a mission
     * the UI shows as unstaffed is one that genuinely cannot start.
     */
    private void requireFullyStaffed(MissionEntity mission) {
        if (mission.getRequirements().isEmpty()) {
            throw new MissionUnderstaffedException(List.of());
        }

        Map<UUID, Integer> accepted = staffing.acceptedCounts(
                mission.getRequirements().stream().map(CrewRequirementEntity::getId).toList());

        List<MissionUnderstaffedException.Shortfall> shortfalls = mission.getRequirements().stream()
                .filter(requirement -> accepted.getOrDefault(requirement.getId(), 0)
                        < requirement.getRequiredCount())
                .map(requirement -> new MissionUnderstaffedException.Shortfall(
                        requirement.getId(),
                        requirement.getTitle(),
                        requirement.getRequiredCount(),
                        accepted.getOrDefault(requirement.getId(), 0)))
                .sorted(Comparator.comparing(MissionUnderstaffedException.Shortfall::title,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (!shortfalls.isEmpty()) {
            throw new MissionUnderstaffedException(shortfalls);
        }
    }

    private void requireTransition(MissionEntity mission, MissionStatus target) {
        if (!mission.getStatus().canTransitionTo(target)) {
            throw new InvalidMissionTransitionException(mission.getStatus(), target);
        }
    }

    /**
     * A closed mission is terminal - M3.
     *
     * <p>Editing is not a transition, so this needs saying separately. It is reported as a refused
     * move to PLAN because that is what editing an approved mission would otherwise do.
     */
    private void requireNotClosed(MissionEntity mission) {
        if (mission.getStatus().isTerminal()) {
            throw new InvalidMissionTransitionException(mission.getStatus(), MissionStatus.PLAN);
        }
    }

    /**
     * BR-11. A mission that ran to its end is complete; anything stopped earlier was aborted.
     *
     * <p>{@code REJECTED} cannot be chosen freely: a close reason contradicting the mission's own
     * history would make the record actively misleading. Feature 05 closes a rejected mission
     * through this same path, and for that mission the reason is legitimate.
     */
    private MissionCloseReason closeReasonFor(MissionEntity mission, MissionCloseReason requested) {
        if (requested == null) {
            return mission.getStatus() == MissionStatus.ACTIVE
                    ? MissionCloseReason.COMPLETED
                    : MissionCloseReason.ABORTED;
        }
        if (requested == MissionCloseReason.REJECTED
                && mission.getStatus() != MissionStatus.REJECTED) {
            throw new MissionValidationException(
                    "A mission can only be closed as REJECTED if it was rejected.");
        }
        return requested;
    }

    /** Invariant M1. Not expressible as a field annotation, because it relates two fields. */
    private static void requireOrderedDates(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new MissionValidationException("endsAt must be after startsAt.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Turns a search term into a LIKE pattern, or null when there is nothing to search for.
     *
     * <p>Wildcards are escaped before the surrounding ones are added, so a search for an
     * underscore does not quietly match every character. Lowercased here rather than in the query
     * so the database is not asked to call lower on a constant for every row.
     */
    private static String namePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }
}
