package com.missioncontrol.crew.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
 * The crew-domain profile for a user who flies.
 *
 * <p>Thin by design. Availability and assignment history are both derived from assignments, which
 * this module neither owns nor reads, so the only thing stored here beyond an identity is the set
 * of skill ratings below.
 *
 * <p>{@code organisationId} and {@code userId} are bare UUIDs with no foreign key: both belong to
 * {@code identity} and architecture.md forbids a constraint across a module boundary.
 * {@code userId} is unique in the schema, which is invariant C1 - a user has at most one crew
 * profile.
 *
 * <p>No setters. This entity is read-only for now; crew self-service editing is deferred and will
 * add named methods that say why a field changed, the same way {@code MissionEntity} does.
 */
@Entity
@Table(name = "crew_member")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class CrewMemberEntity {

    @Id
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    /** Unique, and that user has role {@code CREW_MEMBER} - invariant C1. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * A {@code Set} rather than a {@code List} so the roster read can fetch-join it. That single
     * query for every crew member and every rating is what feature 06's NFR-2 asks for; a bag
     * would work here today but would raise {@code MultipleBagFetchException} the moment a second
     * collection joined it, which is the trap {@code MissionEntity} already documents.
     */
    @OneToMany(mappedBy = "crewMember", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<CrewSkillEntity> skills = new LinkedHashSet<>();
}
