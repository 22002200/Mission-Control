package com.missioncontrol.mission.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A skill that a crew requirement calls for, at a minimum level.
 *
 * <p>The primary key is the pair {@code (crewRequirementId, skillId)}, which is invariant M10
 * expressed as a constraint rather than as a check in application code - two concurrent writes
 * cannot both add the same skill. It follows the composite key {@code crew_skill} already uses for
 * the mirror-image rule on the crew side.
 *
 * <p>{@code skillId} is a bare UUID: the catalogue belongs to {@code skill}, so no foreign key
 * crosses the boundary and the name is resolved through that module's published lookup on read.
 */
@Entity
@Table(name = "required_skill")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class RequiredSkillEntity {

    @EmbeddedId
    private RequiredSkillId id;

    /**
     * Shares its column with the id above, via {@code MapsId}, so the pair really is the key
     * rather than a unique constraint bolted onto a surrogate.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("crewRequirementId")
    @JoinColumn(name = "crew_requirement_id", nullable = false)
    private CrewRequirementEntity requirement;

    /**
     * 1 to 5, matching the scale crew rate themselves on.
     *
     * <p>{@code short} because the column is {@code SMALLINT}, which is what {@code crew_skill}
     * uses for the proficiency this is compared against. Hibernate validates the mapped JDBC type
     * against the real one, so an {@code int} here fails the build at startup.
     */
    @Column(name = "minimum_proficiency", nullable = false)
    private short minimumProficiency;

    /**
     * True makes this a hard filter as well as a scored term; false leaves it as a preference.
     * Feature 06 is what reads it.
     */
    @Column(nullable = false)
    private boolean mandatory;

    /** Relative importance when ranking candidates. Defaults to 1. */
    @Column(nullable = false)
    private int weight;

    UUID skillId() {
        return id.getSkillId();
    }

    void attachTo(CrewRequirementEntity owner) {
        this.requirement = owner;
        this.id = new RequiredSkillId(owner.getId(), id.getSkillId());
    }
}
