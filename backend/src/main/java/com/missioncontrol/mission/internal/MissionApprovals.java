package com.missioncontrol.mission.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The module's single point of contact with the approval ledger.
 *
 * <p>Nothing else names {@link MissionApprovalRepository}, which is what lets both services here
 * write to the ledger without depending on each other. {@code MissionService} needs it because
 * closing a mission has to settle a cycle that was still open; {@code MissionApprovalService}
 * needs it for everything else. A component between them rather than one service calling the
 * other keeps the arrows one-way and leaves no place for the transition rules to be restated -
 * those stay on {@link MissionStatus}. Same shape as the existing {@link MissionStaffing} wrapper.
 */
@Component
class MissionApprovals {

    private final MissionApprovalRepository approvals;

    MissionApprovals(MissionApprovalRepository approvals) {
        this.approvals = approvals;
    }

    /** Opens a cycle on submission - FR-1. Invariant M8 is the partial unique index behind it. */
    void open(MissionEntity mission, UUID submittedBy, Instant now) {
        approvals.save(MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(mission.getOrganisationId())
                .missionId(mission.getId())
                .submittedBy(submittedBy)
                .submittedAt(now)
                .decision(ApprovalDecision.PENDING)
                .build());
    }

    /**
     * Records a director's decision on the open cycle - FR-2, FR-3.
     *
     * <p>A mission in {@code PENDING_APPROVAL} always has an open cycle: submit is the only way
     * into that status and it opens one in the same transaction, and the seed data carries cycles
     * for every mission it puts past {@code PLAN}. So a missing cycle here means the ledger
     * contradicts the mission's own status, which is a bug to find rather than a state to paper
     * over - hence an {@code IllegalStateException} and a 500, not a tidy 409 that would hide it.
     */
    void settle(MissionEntity mission, ApprovalDecision outcome, UUID decidedBy, String comment,
                Instant now) {
        openCycle(mission)
                .orElseThrow(() -> new IllegalStateException(
                        "Mission " + mission.getId() + " is " + mission.getStatus()
                                + " but has no open approval cycle to decide."))
                .settle(outcome, decidedBy, comment, now);
    }

    /**
     * Settles any open cycle as {@code CANCELLED}, because the mission is being closed.
     *
     * <p>Lenient where {@link #settle} is strict, and for a good reason: a mission closed from
     * {@code PLAN}, {@code APPROVED}, {@code ACTIVE} or {@code REJECTED} has nothing open and
     * nothing is wrong. It still asks, rather than testing the status first, so that a stray open
     * cycle is swept up rather than left to block the mission's unique index forever.
     *
     * <p>The close comment is carried onto the cycle. A history entry reading 'Cancelled - launch
     * window missed' says more than a bare Cancelled, and the two columns are the same width.
     */
    void cancelOpen(MissionEntity mission, UUID cancelledBy, String comment, Instant now) {
        openCycle(mission).ifPresent(
                cycle -> cycle.settle(ApprovalDecision.CANCELLED, cancelledBy, comment, now));
    }

    /** Every cycle on the mission, newest first - FR-6. */
    List<MissionApprovalEntity> history(MissionEntity mission) {
        return approvals.findHistory(mission.getId(), mission.getOrganisationId());
    }

    private Optional<MissionApprovalEntity> openCycle(MissionEntity mission) {
        return approvals.findByMissionIdAndOrganisationIdAndDecision(
                mission.getId(), mission.getOrganisationId(), ApprovalDecision.PENDING);
    }
}
