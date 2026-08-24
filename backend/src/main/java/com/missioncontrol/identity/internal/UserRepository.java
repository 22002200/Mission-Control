package com.missioncontrol.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Looks a user up by email, case-insensitively - invariant I1.
     *
     * <p>Written by hand rather than derived as {@code findByEmailIgnoreCase}, which Spring Data
     * renders as {@code upper(email) = upper(?)}. The database has a unique index on
     * {@code LOWER(email)}, and {@code upper(...)} cannot use it, so the derived version degrades
     * to a sequential scan whose cost grows with the table - and whose timing therefore varies
     * with whether the row exists, which is exactly what NFR-2 forbids.
     *
     * <p>The organisation is fetched eagerly because every caller needs its name.
     */
    @Query("select u from UserEntity u join fetch u.organisation "
            + "where lower(u.email) = lower(:email)")
    Optional<UserEntity> findByEmailIgnoringCase(@Param("email") String email);

    @Query("select u from UserEntity u join fetch u.organisation where u.id = :id")
    Optional<UserEntity> findByIdWithOrganisation(@Param("id") UUID id);

    /** The two-column read on the authenticated request path. */
    @Query("select new com.missioncontrol.identity.internal.TokenValidity(u.tokensValidFrom, u.status) "
            + "from UserEntity u where u.id = :id")
    Optional<TokenValidity> findTokenValidity(@Param("id") UUID id);
}
