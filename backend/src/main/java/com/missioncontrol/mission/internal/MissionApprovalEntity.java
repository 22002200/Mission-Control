package com.missioncontrol.mission.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One submit-and-decide cycle on a mission.
 *
 * <p>A row per cycle rather than a rejection reason on the mission, so the REJECTED to PLAN to
 * resubmit loop keeps its whole history instead of overwriting the last answer - FR-7 and BR-9.
 *
 * <p><strong>{@code missionId} is a bare UUID, and {@link MissionEntity} has no collection of
 * these.</strong> That is deliberate on three counts. An approval is a ledger entry beside the
 * mission, not part of the aggregate, so nothing that reads a mission should pay for it -
 * {@code findDetailByIdAndOrganisationId} already fetch-joins two collections and a third would
 * multiply the mission row out further for data no mission response carries. And the association
 * that would express it, {@code cascade = ALL} with {@code orphanRemoval}, is a deletion waiting
 * to happen next to a record NFR-3 says is append-only. The database still has a real intra-module
 * foreign key with a delete cascade, so a deleted mission takes its history with it; that is the
 * database's job rather than Hibernate's.
 *
 * <p>No setters, like every other entity here. The only write this type accepts after its insert
 * is {@link #settle}.
 */
@Entity
@Table(name = "mission_approval")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class MissionApprovalEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    /** The mission lead who submitted the plan. BR-2 means this is always the mission's owner. */
    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** The director who decided, or the caller who closed the mission. Null while pending. */
    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Convert(converter = ApprovalDecisionConverter.class)
    @Column(nullable = false)
    private ApprovalDecision decision;

    /** The rejection reason, or a note left with an approval. */
    @Column(length = 1000)
    private String comment;

    /**
     * Records the decision, closing this cycle for good.
     *
     * <p>Sets the decision, the decider and the instant together, so invariant NFR-4 - every
     * decision says who made it and when - cannot be violated even momentarily. The database
     * states the same rule as two check constraints.
     *
     * <p>Refuses a second settlement outright, and deliberately as an {@code IllegalStateException}
     * rather than an API problem. Reaching here twice means two callers got past the mission's own
     * status check, which is a bug in the guard rather than anything the caller did wrong, and
     * dressing it up as a 409 would hide it.
     */
    void settle(ApprovalDecision outcome, UUID decidedBy, String comment, Instant now) {
        if (this.decision != ApprovalDecision.PENDING) {
            throw new IllegalStateException(
                    "Approval " + id + " is already " + this.decision + " and cannot be decided again.");
        }
        this.decision = outcome;
        this.decidedBy = decidedBy;
        this.decidedAt = now;
        this.comment = comment == null || comment.isBlank() ? null : comment.strip();
    }

    boolean isPending() {
        return decision == ApprovalDecision.PENDING;
    }
}
