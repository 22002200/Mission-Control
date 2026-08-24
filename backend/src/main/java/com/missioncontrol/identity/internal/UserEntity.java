package com.missioncontrol.identity.internal;

import com.missioncontrol.shared.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An account that can log in.
 *
 * <p>The table is {@code app_user} because {@code USER} is reserved in PostgreSQL.
 *
 * <p>The association to {@link OrganisationEntity} is a real one, with a real foreign key, because
 * both live in this module - {@code /api/auth/me} has to return the organisation's name, and a
 * join beats a second query. Associations across a module boundary would be an id and no
 * constraint.
 *
 * <p>No setters, and no {@code @PreUpdate}. State changes go through named methods so the reason
 * for a write is visible at the call site, and so 'now' stays something a test can control rather
 * than something the persistence layer reaches for on its own.
 */
@Entity
@Table(name = "app_user")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class UserEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private OrganisationEntity organisation;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Convert(converter = UserRoleConverter.class)
    @Column(nullable = false)
    private UserRole role;

    @Convert(converter = UserStatusConverter.class)
    @Column(nullable = false)
    private UserStatus status;

    /** JWTs issued before this instant are rejected. Null until the user first logs out. */
    @Column(name = "tokens_valid_from")
    private Instant tokensValidFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Invalidates every token issued to this user before {@code now}.
     *
     * <p>Deliberately not per-device: there is no token table, so logging out anywhere logs out
     * everywhere. See the open question in {@code docs/data-model.md}.
     */
    void revokeTokensIssuedBefore(Instant now) {
        this.tokensValidFrom = now;
        this.updatedAt = now;
    }
}
