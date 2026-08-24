package com.missioncontrol.mission.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MissionRepository extends JpaRepository<MissionEntity, UUID> {

    /**
     * The tenant-scoped single read - invariant T1.
     *
     * <p>Scoped in the query rather than by fetching and then comparing, so a mission in another
     * organisation is genuinely not found. That is what makes a cross-tenant request a 404 and not
     * a 403.
     */
    Optional<MissionEntity> findByIdAndOrganisationId(UUID id, UUID organisationId);

    /**
     * The same read, with the requirements and their skills already attached.
     *
     * <p>Two collections fetched in one query, which only works because both are mapped as sets -
     * a pair of bags raises {@code MultipleBagFetchException}. This is what keeps the detail
     * endpoint bounded rather than costing a query per requirement (NFR-1).
     *
     * <p>{@code distinct} is needed because the joins multiply the mission row out.
     */
    @Query("""
            select distinct m from MissionEntity m
            left join fetch m.requirements r
            left join fetch r.requiredSkills
            where m.id = :id and m.organisationId = :organisationId
            """)
    Optional<MissionEntity> findDetailByIdAndOrganisationId(
            @Param("id") UUID id,
            @Param("organisationId") UUID organisationId);

    /**
     * The list as a director or a mission lead sees it - FR-2, FR-3.
     *
     * <p>{@code leadId} null means the whole organisation, which is the director case; supplying
     * it narrows to one owner. {@code statuses} is never null - a caller wanting everything passes
     * every status, because an {@code in} over an empty or absent collection is not expressible in
     * JPQL without either SpEL or a second query, and a filter that silently does nothing is worse
     * than an explicit list.
     *
     * <p>The order is fixed here rather than exposed as a sort parameter, because FR-3 names
     * exactly one order and a sortable column is a promise about the schema. The id breaks ties so
     * paging stays stable when two missions start at the same instant - without it a row can show
     * up on two pages, or on none.
     */
    @Query("""
            select m from MissionEntity m
            where m.organisationId = :organisationId
              and m.status in :statuses
              and (:leadId is null or m.missionLeadId = :leadId)
              and (:namePattern is null or lower(m.name) like :namePattern escape '\\')
            order by m.startsAt asc, m.id asc
            """)
    Page<MissionEntity> findForOrganisation(@Param("organisationId") UUID organisationId,
                                            @Param("leadId") UUID leadId,
                                            @Param("statuses") Collection<MissionStatus> statuses,
                                            @Param("namePattern") String namePattern,
                                            Pageable pageable);

    /**
     * The list as a crew member sees it: only missions they hold an assignment on - FR-2.
     *
     * <p>A separate method rather than another optional parameter on the one above. The ids come
     * from the assignment read model, so this is a different question with a different source,
     * and expressing it as a nullable {@code in} clause would need SpEL that reads far worse than
     * two queries. The service never calls this with an empty set; it returns an empty page
     * without touching the database instead.
     */
    @Query("""
            select m from MissionEntity m
            where m.organisationId = :organisationId
              and m.id in :missionIds
              and m.status in :statuses
              and (:namePattern is null or lower(m.name) like :namePattern escape '\\')
            order by m.startsAt asc, m.id asc
            """)
    Page<MissionEntity> findAssigned(@Param("organisationId") UUID organisationId,
                                     @Param("missionIds") Collection<UUID> missionIds,
                                     @Param("statuses") Collection<MissionStatus> statuses,
                                     @Param("namePattern") String namePattern,
                                     Pageable pageable);

    /**
     * Requirement totals for a page of missions, in one query.
     *
     * <p>A projection rather than the entities: a list card needs the counts, not the requirements
     * themselves. Missions with no requirements are absent from the result, which the caller reads
     * as zero.
     */
    @Query("""
            select new com.missioncontrol.mission.internal.RequirementTotals(
                r.mission.id, r.id, r.requiredCount)
            from CrewRequirementEntity r
            where r.mission.id in :missionIds
            """)
    List<RequirementTotals> findRequirementTotals(@Param("missionIds") Collection<UUID> missionIds);
}
