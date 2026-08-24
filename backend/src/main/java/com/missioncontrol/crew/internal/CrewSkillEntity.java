package com.missioncontrol.crew.internal;

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
 * One crew member's rated proficiency in one catalogue skill.
 *
 * <p>The primary key is the pair {@code (crewMemberId, skillId)}, which is invariant C2 expressed
 * as a constraint rather than as a check in application code - two concurrent writes cannot both
 * add the same skill to the same person.
 *
 * <p>{@code skillId} is a bare UUID: the catalogue belongs to {@code skill}, so no foreign key
 * crosses the boundary and the name is resolved through that module's published lookup on read.
 */
@Entity
@Table(name = "crew_skill")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CrewSkillEntity {

    @EmbeddedId
    private CrewSkillId id;

    /**
     * Shares its column with the id above, via {@code MapsId}, so the pair really is the key
     * rather than a unique constraint bolted onto a surrogate.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("crewMemberId")
    @JoinColumn(name = "crew_member_id", nullable = false)
    private CrewMemberEntity crewMember;

    /**
     * 1 to 5 - invariant C3, also enforced by a check constraint in the changelog.
     *
     * <p>{@code short} because the column is {@code SMALLINT}. Hibernate validates the mapped JDBC
     * type against the real one, so an {@code int} here fails the build at startup rather than at
     * the first read.
     */
    @Column(nullable = false)
    private short proficiency;

    UUID skillId() {
        return id.getSkillId();
    }
}
