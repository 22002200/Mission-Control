package com.missioncontrol.assignment.internal;

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
 * One offer of one place on one mission to one crew member.
 *
 * <p>Every id here is a bare UUID with no foreign key. The mission and its requirement belong to
 * {@code mission}, the crew member to {@code crew} and the organisation to {@code identity}, and
 * architecture.md forbids a constraint across a module boundary. This module has no association of
 * its own to model, so unlike {@code Mission} and {@code CrewRequirement} there is not a single
 * real foreign key on the table.
 *
 * <p>No setters, like every other entity here. Each of the three ways out of {@code OFFERED} is a
 * named method that says why the status moved, and all three take the current instant as an
 * argument so a test can control now and so the reason for a write is legible at the call site.
 *
 * <p>Nothing here decides who is allowed to ask, and nothing here checks the schedule. Both are the
 * service's job - this type's responsibility is that a change is applied consistently once it has
 * been permitted. What it does refuse is an illegal transition, because that is a property of the
 * row and not of the request.
 */
@Entity
@Table(name = "assignment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class AssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;

    @Column(name = "crew_requirement_id", nullable = false)
    private UUID crewRequirementId;

    @Column(name = "crew_member_id", nullable = false)
    private UUID crewMemberId;

    @Convert(converter = AssignmentStatusConverter.class)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    /**
     * When the assignment stopped being an open offer - null while {@code OFFERED}.
     *
     * <p>Set by all three exits, withdrawal included, which is wider than the data model's original
     * wording of 'null until accepted or declined'. NFR-6 wants every status change timestamped and
     * a withdrawal is one; read this as when it was settled rather than when the crew member
     * replied. The database states the same rule as a check constraint.
     */
    @Column(name = "responded_at")
    private Instant respondedAt;

    /** {@code OFFERED} to {@code ACCEPTED} - FR-4. The schedule check that guards this is the service's. */
    void accept(Instant now) {
        settle(AssignmentStatus.ACCEPTED, now);
    }

    /** {@code OFFERED} to {@code DECLINED} - FR-5. Terminal: the place is free for someone else. */
    void decline(Instant now) {
        settle(AssignmentStatus.DECLINED, now);
    }

    /**
     * {@code OFFERED} or {@code ACCEPTED} to {@code WITHDRAWN} - FR-6.
     *
     * <p>The one exit reachable from an acceptance, and the owning mission lead's alone - BR-9. It
     * is also what closing a mission does to its outstanding offers, which is why this is not
     * restricted to a caller-facing path.
     */
    void withdraw(Instant now) {
        settle(AssignmentStatus.WITHDRAWN, now);
    }

    /**
     * Moves the status and stamps the instant together, so NFR-6 cannot be violated even
     * momentarily.
     *
     * <p>Refuses an illegal move as an {@code IllegalStateException} rather than an API problem, the
     * same choice {@code MissionApprovalEntity.settle} makes. Reaching here from a terminal state
     * means the service's own transition check let it through, which is a bug in the guard rather
     * than anything the caller did wrong, and dressing it up as a 409 would hide it.
     */
    private void settle(AssignmentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Assignment " + id + " is " + status + " and cannot move to " + target + ".");
        }
        this.status = target;
        this.respondedAt = now;
    }

    boolean isOfferedTo(UUID candidateCrewMemberId) {
        return crewMemberId.equals(candidateCrewMemberId);
    }
}
