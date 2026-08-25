package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.mission.api.RequirementSeat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * The same read, taking a write lock on the mission row - {@code select ... for update}.
     *
     * <p><strong>Every command that changes a mission's status starts here.</strong> Not only the
     * approval ones: a lock that one side of a race skips is not a lock. Dirty checking issues
     * {@code update mission set status = ? where id = ?} with no status predicate, so an
     * unsynchronised {@code close} that read {@code PENDING_APPROVAL} will block on this lock and
     * then happily overwrite the {@code APPROVED} that won - leaving an approval record next to a
     * mission whose status contradicts it. The database cannot stop that; this can.
     *
     * <p>Deliberately <strong>not</strong> the fetch-join query above. PostgreSQL refuses
     * {@code for update} on the nullable side of an outer join, so locking and eager-loading the
     * requirements cannot be one statement. Lock here first, then read the detail: the second
     * query returns the same managed instance, so it costs one extra round trip and no N+1.
     *
     * <p>Two things about this are invisible in the code and easy to break later.
     * <strong>This must be the first entity load in the transaction</strong> - Hibernate's
     * first-level cache will hand back a copy loaded before the lock was taken, which would defeat
     * the whole thing. And <strong>{@code READ COMMITTED} is load-bearing</strong>: 'block, then
     * see the value the winner committed' is specific to it, and under {@code REPEATABLE READ}
     * PostgreSQL raises a serialization failure instead. Never set an isolation level on these
     * methods.
     *
     * <p>No lock timeout hint either. On PostgreSQL Hibernate can only express {@code NOWAIT} and
     * {@code SKIP LOCKED}; a positive millisecond timeout is silently ignored, and {@code NOWAIT}
     * would turn the 409 the loser is supposed to get into a 500. These transactions read one row
     * and write two, so waiting is measured in microseconds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MissionEntity m where m.id = :id and m.organisationId = :organisationId")
    Optional<MissionEntity> lockByIdAndOrganisationId(
            @Param("id") UUID id,
            @Param("organisationId") UUID organisationId);

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

    /**
     * Missions by id, tenant-scoped, for the published window lookup.
     *
     * <p>No fetch join. {@code MissionWindow} carries no requirements, precisely so that resolving
     * a page of a crew member's assignments cannot fan out into every staffing line of every
     * mission they have ever been on.
     */
    @Query("select m from MissionEntity m where m.organisationId = :organisationId and m.id in :ids")
    List<MissionEntity> findWindows(@Param("ids") Collection<UUID> ids,
                                    @Param("organisationId") UUID organisationId);

    /**
     * Requirement capacity by id, tenant-scoped, as a projection.
     *
     * <p>A projection rather than the entities for the same reason {@link #findRequirementTotals}
     * is one: the caller needs a title and a number, not a requirement and its skills. Scoped on
     * the requirement's own {@code organisationId} - the column duplicated onto it for exactly
     * this - so the query does not have to join the mission to be tenant-safe.
     */
    @Query("""
            select new com.missioncontrol.mission.api.RequirementSeat(
                r.id, r.mission.id, r.title, r.requiredCount)
            from CrewRequirementEntity r
            where r.organisationId = :organisationId and r.id in :ids
            """)
    List<RequirementSeat> findRequirementSeats(@Param("ids") Collection<UUID> ids,
                                               @Param("organisationId") UUID organisationId);

    /**
     * The median length of the organisation's completed missions, in seconds.
     *
     * <p>Native, and it has to be. {@code percentile_cont} is an ordered-set aggregate - the
     * {@code within group (order by ...)} form - and JPQL has no way to express one, so this
     * cannot be written as a {@code Query} over the entity model however much tidier that would
     * read.
     *
     * <p>Epoch seconds rather than an {@code interval}. Hibernate maps the aggregate over a
     * {@code double} straight onto a {@code Double}; over an interval it would need a converter
     * for a value that is about to be turned into a {@link java.time.Duration} anyway.
     *
     * <p>The status and reason arrive as parameters rather than as literals in the SQL. The pinned
     * codes live on {@link MissionStatus} and {@link MissionCloseReason} and are documented as
     * append-only; writing 6 and 1 here would put a second, silent copy of that mapping in a
     * string where no test would ever catch it drifting.
     *
     * @return null when the organisation has completed no missions - {@code percentile_cont} over
     *         an empty set is null, not zero, which is exactly the distinction the caller needs.
     */
    @Query(value = """
            select percentile_cont(0.5) within group (
                       order by extract(epoch from (m.ends_at - m.starts_at)))
            from mission m
            where m.organisation_id = :organisationId
              and m.status = :closedCode
              and m.close_reason = :completedCode
            """, nativeQuery = true)
    Double findMedianCompletedDurationSeconds(@Param("organisationId") UUID organisationId,
                                              @Param("closedCode") int closedCode,
                                              @Param("completedCode") int completedCode);
}
