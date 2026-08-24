package com.missioncontrol.crew.internal;

import java.util.List;
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
}
