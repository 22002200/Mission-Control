package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A planned or running mission.
 *
 * <p>{@code organisationId}, {@code missionLeadId} and {@code createdBy} are bare UUIDs with no
 * foreign key: organisations and users belong to {@code identity} and architecture.md forbids a
 * constraint across a module boundary. The requirements below are a real association with a real
 * foreign key, because they belong to this module and have no meaning apart from their mission.
 *
 * <p>No setters. Every change goes through a named method that says why it happened and takes the
 * current instant as an argument, so a test can control 'now' and so the reason for a write is
 * legible at the call site rather than inferred from which fields moved.
 *
 * <p>Nothing here enforces which transitions are legal - that is {@link MissionStatus} - and
 * nothing here decides who is allowed to ask. Both are the service's job; this type's
 * responsibility is that a change is applied consistently once it has been permitted.
 */
@Entity
@Table(name = "mission")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class MissionEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Convert(converter = MissionStatusConverter.class)
    @Column(nullable = false)
    private MissionStatus status;

    /** Non-null exactly when {@link #status} is {@code CLOSED} - invariant M4. */
    @Convert(converter = MissionCloseReasonConverter.class)
    @Column(name = "close_reason")
    private MissionCloseReason closeReason;

    /** Optional note recorded when the mission was closed. */
    @Column(name = "close_comment", length = 1000)
    private String closeComment;

    /** The owning user, always a {@code MISSION_LEAD} in this organisation - invariant M2. */
    @Column(name = "mission_lead_id", nullable = false)
    private UUID missionLeadId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * A {@code Set}, not a {@code List}, so that this and the required skills below it can both be
     * fetch-joined in one query without Hibernate raising {@code MultipleBagFetchException}. That
     * single query is how the detail read stays bounded - NFR-1.
     */
    @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("title asc")
    @Builder.Default
    private Set<CrewRequirementEntity> requirements = new LinkedHashSet<>();

    /**
     * Applies an edit, and drops an already-approved mission back to planning.
     *
     * <p>That second part is invariant M5, and it is the whole reason editing is not a plain
     * field assignment: the spec requires an edited mission to be resubmitted, so the approval it
     * was carrying no longer describes what was approved. A mission still in {@code PLAN},
     * {@code PENDING_APPROVAL} or {@code REJECTED} keeps its status.
     *
     * <p>A null argument means 'leave this alone', so a caller can change one field without
     * restating the rest.
     */
    void updateDetails(String name, String description, Instant startsAt, Instant endsAt,
                       Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description;
        }
        if (startsAt != null) {
            this.startsAt = startsAt;
        }
        if (endsAt != null) {
            this.endsAt = endsAt;
        }
        if (status == MissionStatus.APPROVED || status == MissionStatus.ACTIVE) {
            this.status = MissionStatus.PLAN;
        }
        this.updatedAt = now;
    }

    /**
     * {@code PLAN} to {@code PENDING_APPROVAL} - feature 05, FR-1.
     *
     * <p>The requirement that a mission have something to staff before it can be submitted (M12)
     * is the service's check, not this one, for the same reason the transition table is
     * {@link MissionStatus}'s: this type's job is applying a change consistently once it has been
     * permitted.
     */
    void submit(Instant now) {
        this.status = MissionStatus.PENDING_APPROVAL;
        this.updatedAt = now;
    }

    /** {@code PENDING_APPROVAL} to {@code APPROVED} - FR-2. Who decided is on the approval cycle. */
    void approve(Instant now) {
        this.status = MissionStatus.APPROVED;
        this.updatedAt = now;
    }

    /**
     * {@code PENDING_APPROVAL} to {@code REJECTED} - FR-3.
     *
     * <p>The reason is not stored here. {@code closeComment} belongs to closing, and BR-9 needs
     * every rejection kept rather than the most recent one overwriting its predecessor - which is
     * the whole reason {@code MissionApproval} is a row per cycle.
     */
    void reject(Instant now) {
        this.status = MissionStatus.REJECTED;
        this.updatedAt = now;
    }

    /**
     * {@code REJECTED} back to {@code PLAN} - FR-4.
     *
     * <p>Nothing is unwound. BR-9 leaves the approval history intact and the next submission opens
     * a new cycle, so the record of what was rejected and why survives the second attempt.
     */
    void replan(Instant now) {
        this.status = MissionStatus.PLAN;
        this.updatedAt = now;
    }

    /** {@code APPROVED} to {@code ACTIVE}. The staffing check that guards this is the service's. */
    void start(Instant now) {
        this.status = MissionStatus.ACTIVE;
        this.updatedAt = now;
    }

    /**
     * Ends the mission.
     *
     * <p>Sets the reason in the same step as the status, so invariant M4 cannot be violated even
     * momentarily - there is no window in which a mission is closed without saying why.
     */
    void close(MissionCloseReason reason, String comment, Instant now) {
        this.status = MissionStatus.CLOSED;
        this.closeReason = reason;
        this.closeComment = comment == null || comment.isBlank() ? null : comment;
        this.updatedAt = now;
    }

    void addRequirement(CrewRequirementEntity requirement, Instant now) {
        requirement.attachTo(this);
        this.requirements.add(requirement);
        this.updatedAt = now;
    }

    void removeRequirement(CrewRequirementEntity requirement, Instant now) {
        this.requirements.remove(requirement);
        this.updatedAt = now;
    }

    /** Marks the aggregate as touched when a requirement below it changed. */
    void touch(Instant now) {
        this.updatedAt = now;
    }

    boolean isOwnedBy(UUID userId) {
        return missionLeadId.equals(userId);
    }
}
