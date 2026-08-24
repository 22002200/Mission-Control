package com.missioncontrol.skill.internal;

import jakarta.persistence.Column;
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
 * One entry in an organisation's skill catalogue.
 *
 * <p>{@code organisationId} is a bare UUID rather than an association: {@code Organisation} belongs
 * to {@code identity}, and architecture.md forbids foreign keys across a module boundary. The
 * column carries no constraint in the schema either - see the skill changelog.
 *
 * <p>No setters. This entity is read-only for now; the write endpoints will add named methods that
 * say why a field changed, the same way {@code UserEntity} does.
 */
@Entity
@Table(name = "skill")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class SkillEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    /** False retires the skill without deleting it - invariant S2. */
    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
