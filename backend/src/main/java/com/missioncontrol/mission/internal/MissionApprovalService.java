package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Getting a mission past a director, and the record of every attempt.
 *
 * <p>Separate from {@link MissionService} because this is the one place in the product where the
 * roles genuinely divide: a mission lead proposes, a director decides, and invariant M2 - directors
 * cannot own missions - means a director can never be deciding their own work (BR-8). Keeping the
 * two services apart is what stops either set of guards being applied to the other's endpoints.
 *
 * <p>Every method here is one transaction covering both the mission's status and its approval
 * record - NFR-1 - and every one of them starts at
 * {@link MissionLoader#visibleForUpdate}, which is what makes NFR-2 true rather than likely: two
 * directors deciding at once serialise on the mission row, and the loser reads the status the
 * winner committed and is refused with it.
 *
 * <p>Checks run in the order their answers may safely be revealed: visibility (404), then
 * authorisation (403), then state (409), then the mission's own contents (409). Answering 403
 * before 404 would confirm a mission exists to a caller with no business knowing it does.
 *
 * <p>The role check for approve and reject is <strong>not</strong> here. BR-3 is a pure role
 * question that needs no mission in hand, so it lives on the controller as {@code @PreAuthorize} -
 * the same division {@link MissionAccess} documents. A director from another organisation still
 * gets a 404, because the annotation only asks what role they hold and the query does the rest.
 */
@Service
class MissionApprovalService {

    private final MissionLoader loader;
    private final MissionAccess access;
    private final MissionApprovals approvals;
    private final MissionDetailAssembler assembler;
    private final CurrentUser currentUser;
    private final Clock clock;

    MissionApprovalService(MissionLoader loader,
                           MissionAccess access,
                           MissionApprovals approvals,
                           MissionDetailAssembler assembler,
                           CurrentUser currentUser,
                           Clock clock) {
        this.loader = loader;
        this.access = access;
        this.approvals = approvals;
        this.assembler = assembler;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** FR-1. {@code PLAN} to {@code PENDING_APPROVAL}, opening a cycle for the director. */
    @Transactional
    MissionResponse submit(UUID id) {
        MissionEntity mission = loader.visibleForUpdate(id);
        access.requireIsOwner(mission, "submit this mission for approval");
        requireTransition(mission, MissionStatus.PENDING_APPROVAL);
        requireHasRequirements(mission);

        Instant now = clock.instant();
        mission.submit(now);
        approvals.open(mission, currentUser.userId(), now);
        return assembler.detail(mission);
    }

    /** FR-2. {@code PENDING_APPROVAL} to {@code APPROVED}, settling the open cycle. */
    @Transactional
    MissionResponse approve(UUID id, ApproveMissionRequest request) {
        return decide(id, MissionStatus.APPROVED, ApprovalDecision.APPROVED, request.comment());
    }

    /** FR-3. {@code PENDING_APPROVAL} to {@code REJECTED}, with the reason the lead has to act on. */
    @Transactional
    MissionResponse reject(UUID id, RejectMissionRequest request) {
        return decide(id, MissionStatus.REJECTED, ApprovalDecision.REJECTED, request.comment());
    }

    /**
     * FR-4. {@code REJECTED} back to {@code PLAN}, so the plan can be revised and resubmitted.
     *
     * <p>Owner-only, which is narrower than invariant M6 allows and is what the spec's API table
     * says. One consequence worth knowing: a director has no way to unstick a rejected mission
     * whose lead is unavailable - their lever is closing it as {@code REJECTED} instead. Note also
     * the asymmetry a caller meets here, which is correct but surprising without the explanation:
     * a <em>director</em> gets 403, because they can see the mission; a <em>non-owning lead</em>
     * gets 404, because they cannot.
     *
     * <p>Nothing is written to the ledger - BR-9. The rejected cycle stays exactly as it was and
     * the next submission opens a new one, which is what makes the history a history.
     */
    @Transactional
    MissionResponse replan(UUID id) {
        MissionEntity mission = loader.visibleForUpdate(id);
        access.requireIsOwner(mission, "return this mission to planning");
        requireRejected(mission);

        mission.replan(clock.instant());
        return assembler.detail(mission);
    }

    /** FR-6. Every cycle, newest first, for anyone who can see the mission. */
    @Transactional(readOnly = true)
    List<MissionApprovalResponse> history(UUID id) {
        MissionEntity mission = loader.visible(id);
        return assembler.approvals(approvals.history(mission), mission.getOrganisationId());
    }

    /**
     * Approve and reject differ only in which two constants they carry.
     *
     * <p>Shared rather than written twice, because the part that must not diverge is the ordering:
     * the mission moves and the cycle is settled inside one transaction, with one instant, so
     * neither can be left without the other (NFR-1).
     */
    private MissionResponse decide(UUID id, MissionStatus target, ApprovalDecision outcome,
                                   String comment) {
        MissionEntity mission = loader.visibleForUpdate(id);
        requireTransition(mission, target);

        Instant now = clock.instant();
        approvals.settle(mission, outcome, currentUser.userId(), comment, now);
        if (target == MissionStatus.APPROVED) {
            mission.approve(now);
        } else {
            mission.reject(now);
        }
        return assembler.detail(mission);
    }

    /**
     * Invariant M12, BR-5.
     *
     * <p>Reads the requirements off the mission the loader already fetch-joined, so this costs no
     * query. Feature 04 refuses the same empty mission at {@code POST /start}; this catches it
     * before a director spends any attention on it.
     */
    private void requireHasRequirements(MissionEntity mission) {
        if (mission.getRequirements().isEmpty()) {
            throw new MissionHasNoRequirementsException();
        }
    }

    /**
     * BR-7, which {@link MissionStatus#canTransitionTo} cannot express on its own.
     *
     * <p>Three states may move to {@code PLAN}: {@code REJECTED} does it through this endpoint, and
     * {@code APPROVED} and {@code ACTIVE} do it as a side effect of being edited - that is M5. The
     * arrow is shared, the reason is not, so asking the transition table whether {@code PLAN} is
     * reachable would let {@code POST /replan} throw away a live approval without anyone editing
     * anything. The source status has to be named explicitly.
     *
     * <p>Reported as a refused move to {@code PLAN} so the response still carries the two
     * properties a client uses to tell a stale view from a mistake.
     */
    private void requireRejected(MissionEntity mission) {
        if (mission.getStatus() != MissionStatus.REJECTED) {
            throw new InvalidMissionTransitionException(mission.getStatus(), MissionStatus.PLAN);
        }
    }

    /**
     * Invariant M3. The table itself lives on {@link MissionStatus}, where it is tested
     * exhaustively without a Spring context.
     *
     * <p>This is also the check that answers a lost race. The loser of two concurrent decisions
     * arrives here having read the status the winner committed, so it reports the real current
     * status rather than a generic conflict - which is the difference between a client knowing to
     * refresh and a client guessing.
     */
    private void requireTransition(MissionEntity mission, MissionStatus target) {
        if (!mission.getStatus().canTransitionTo(target)) {
            throw new InvalidMissionTransitionException(mission.getStatus(), target);
        }
    }
}
