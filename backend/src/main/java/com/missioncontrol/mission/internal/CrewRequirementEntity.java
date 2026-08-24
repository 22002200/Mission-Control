package com.missioncontrol.mission.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One staffing line on a mission: how many people of what kind it needs.
 *
 * <p>Quantity-based rather than one row per seat. Two flight engineers with identical skill
 * requirements are one requirement with a count of two, not two rows that happen to match -
 * assignments count against the requirement, so widening it is a number and not a schema of empty
 * chairs.
 *
 * <p>{@code organisationId} is duplicated from the mission rather than read through the
 * association. It costs a column and buys a tenant-scoped query that does not have to join, which
 * matters because every read here is filtered by organisation.
 */
@Entity
@Table(name = "crew_requirement")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CrewRequirementEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    /** Same module, so a real foreign key is correct here. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private MissionEntity mission;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** At least one - invariant M9, enforced by a check constraint as well as by validation. */
    @Column(name = "required_count", nullable = false)
    private int requiredCount;

    @OneToMany(mappedBy = "requirement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<RequiredSkillEntity> requiredSkills = new LinkedHashSet<>();

    void attachTo(MissionEntity owner) {
        this.mission = owner;
    }

    /**
     * Replaces the whole requirement, skills included.
     *
     * <p>Wholesale rather than field by field because the skills arrive inline on the request -
     * FR-8 - and a partial update of a set has no obvious meaning. Clearing and re-adding lets
     * {@code orphanRemoval} delete the rows that went away, which a merge of the incoming list
     * would not.
     */
    void replaceWith(String title, String description, int requiredCount,
                     Collection<RequiredSkillEntity> skills) {
        this.title = title;
        this.description = description == null || description.isBlank() ? null : description;
        this.requiredCount = requiredCount;
        this.requiredSkills.clear();
        skills.forEach(skill -> {
            skill.attachTo(this);
            this.requiredSkills.add(skill);
        });
    }
}
