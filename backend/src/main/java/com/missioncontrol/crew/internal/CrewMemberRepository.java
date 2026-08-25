package com.missioncontrol.crew.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CrewMemberRepository extends JpaRepository<CrewMemberEntity, UUID> {

    /**
     * The whole roster for one organisation, ratings attached, in one statement.
     *
     * <p>Tenant-scoped in the query rather than by fetching and then filtering, so another
     * organisation's crew are genuinely not returned - invariant T1.
     *
     * <p>The fetch join is the point. Matching scores every crew member against every requirement,
     * so loading the roster and then walking it for ratings would be an N+1 across the entire
     * organisation - up to 500 people by NFR-3. {@code distinct} is needed because the join
     * multiplies each crew member out by their rating count.
     */
    @Query("""
            select distinct c from CrewMemberEntity c
            left join fetch c.skills
            where c.organisationId = :organisationId
            """)
    List<CrewMemberEntity> findRoster(@Param("organisationId") UUID organisationId);

    /**
     * One account's crew profile id, tenant-scoped.
     *
     * <p>Deliberately a scalar rather than the entity. The caller wants an id, and returning a
     * {@code CrewMemberEntity} would drag a lazy rating collection into a method that never reads
     * one - and, with {@code open-in-view} off, into a place it could not read one anyway.
     */
    @Query("""
            select c.id from CrewMemberEntity c
            where c.userId = :userId and c.organisationId = :organisationId
            """)
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId,
                                  @Param("organisationId") UUID organisationId);

    /**
     * Crew profile id to account id, for several at once.
     *
     * <p>An {@code Object[]} pair rather than a projection record, because the one caller turns it
     * straight into a map and a published record for a two-column tuple would outlive its use.
     */
    @Query("""
            select c.id, c.userId from CrewMemberEntity c
            where c.organisationId = :organisationId and c.id in :ids
            """)
    List<Object[]> findUserIdsByIds(@Param("ids") Collection<UUID> ids,
                                    @Param("organisationId") UUID organisationId);
}
