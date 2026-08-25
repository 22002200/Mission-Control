package com.missioncontrol.assignment.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Assignments, always within one organisation - invariant T1.
 *
 * <p>Every query carries the {@code organisationId} predicate even where the caller has already
 * established the tenant some other way, for the same reason {@code MissionApprovalRepository}
 * does: a query that is only safe because of what its one caller happens to do first becomes
 * unsafe the day it gets a second caller.
 *
 * <p>Counts and id pairs rather than fetched entities wherever a number or an id is what is
 * wanted. Invariant A2 is a cap on a count, and materialising rows to call {@code size()} on them
 * would be the easy way to make a hot path expensive.
 */
interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    Optional<AssignmentEntity> findByIdAndOrganisationId(UUID id, UUID organisationId);

    /**
     * How many places on a requirement are spoken for - the left-hand side of invariant A2.
     *
     * <p>Offered and accepted together, because an offer reserves the seat even though it reserves
     * nobody's calendar - A4. Callers must hold the mission's write lock before trusting this: it
     * is a count, and two transactions can read the same count and both act on it.
     */
    @Query("""
            select count(a) from AssignmentEntity a
            where a.crewRequirementId = :requirementId
              and a.organisationId = :organisationId
              and a.status in :statuses
            """)
    long countByRequirement(@Param("requirementId") UUID requirementId,
                            @Param("organisationId") UUID organisationId,
                            @Param("statuses") Collection<AssignmentStatus> statuses);

    /**
     * The crew member's open commitments, write-locked, in a fixed order.
     *
     * <p><strong>This is what makes invariant A3 hold under load</strong>, and it is the second of
     * exactly two locks a staffing command takes. The mission row - taken first, through
     * {@code MissionWindows.lockForUpdate} - serialises two people racing for the last seat on one
     * requirement. It cannot serialise one person accepting two overlapping offers at once, because
     * those are two different missions and two different rows. This can: both transactions lock the
     * same set of that crew member's non-terminal assignments, so the second blocks until the first
     * has committed and then sees the acceptance it has to conflict with.
     *
     * <p>{@code order by a.id} is load-bearing rather than tidiness. Two transactions taking the
     * same rows in opposite orders deadlock; taking them in the same order means one simply waits.
     *
     * <p>The lock order is always the mission first and then this. Closing a mission takes the
     * mission row and then touches only that mission's assignments, so it can never hold one of
     * these rows while waiting for a mission that an acceptance holds - which is what keeps close
     * and accept from deadlocking against each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AssignmentEntity a
            where a.crewMemberId = :crewMemberId
              and a.organisationId = :organisationId
              and a.status in :statuses
            order by a.id
            """)
    List<AssignmentEntity> lockOpenFor(@Param("crewMemberId") UUID crewMemberId,
                                       @Param("organisationId") UUID organisationId,
                                       @Param("statuses") Collection<AssignmentStatus> statuses);

    /** Every assignment on one mission, for the mission's own staffing view - FR-2. */
    @Query("""
            select a from AssignmentEntity a
            where a.missionId = :missionId
              and a.organisationId = :organisationId
              and a.status in :statuses
            order by a.offeredAt asc, a.id asc
            """)
    List<AssignmentEntity> findForMission(@Param("missionId") UUID missionId,
                                          @Param("organisationId") UUID organisationId,
                                          @Param("statuses") Collection<AssignmentStatus> statuses);

    /**
     * One crew member's own assignments, newest offer first - FR-3.
     *
     * <p>{@code a.id desc} breaks ties so ordering stays stable when two offers share an instant -
     * which under a fixed clock in a test they always do. Same reasoning as the {@code m.id asc} in
     * {@code MissionRepository.findForOrganisation}.
     *
     * <p>FR-9's timeframe filter is deliberately not here. It is a predicate on the mission's
     * dates, and missions belong to another module; expressing it in this query would mean joining
     * a table this one does not own.
     */
    @Query("""
            select a from AssignmentEntity a
            where a.crewMemberId = :crewMemberId
              and a.organisationId = :organisationId
              and a.status in :statuses
            order by a.offeredAt desc, a.id desc
            """)
    List<AssignmentEntity> findForCrewMember(@Param("crewMemberId") UUID crewMemberId,
                                             @Param("organisationId") UUID organisationId,
                                             @Param("statuses") Collection<AssignmentStatus> statuses);

    /**
     * Counts in one status for several requirements at once - the published staffing figures.
     *
     * <p>Bulk because a mission list page asks about every requirement on every mission it shows.
     * Requirements nobody holds an assignment against are absent from the result, which is exactly
     * what {@code StaffingReadModel} tells its callers to expect.
     */
    @Query("""
            select a.crewRequirementId, count(a) from AssignmentEntity a
            where a.crewRequirementId in :requirementIds and a.status = :status
            group by a.crewRequirementId
            """)
    List<Object[]> countsByRequirement(@Param("requirementIds") Collection<UUID> requirementIds,
                                       @Param("status") AssignmentStatus status);

    /** The missions one crew member holds any assignment on, in any state - mission visibility. */
    @Query("""
            select distinct a.missionId from AssignmentEntity a
            where a.crewMemberId = :crewMemberId and a.organisationId = :organisationId
            """)
    List<UUID> missionIdsFor(@Param("crewMemberId") UUID crewMemberId,
                             @Param("organisationId") UUID organisationId);

    /** Who holds a non-terminal assignment on one mission - invariant A5, as matching reads it. */
    @Query("""
            select distinct a.crewMemberId from AssignmentEntity a
            where a.missionId = :missionId and a.status in :statuses
            """)
    List<UUID> crewOnMission(@Param("missionId") UUID missionId,
                             @Param("statuses") Collection<AssignmentStatus> statuses);

    /**
     * Every assignment in one status across an organisation, as a crew member and mission pair.
     *
     * <p>Two columns rather than the entities, because the caller is about to ask {@code mission}
     * which of those missions matter and then count. Loading whole rows to read two ids off them
     * would be the same query with more garbage.
     */
    @Query("""
            select a.crewMemberId, a.missionId from AssignmentEntity a
            where a.organisationId = :organisationId and a.status = :status
            """)
    List<Object[]> crewAndMissionsByStatus(@Param("organisationId") UUID organisationId,
                                           @Param("status") AssignmentStatus status);

    /** The same pairs, narrowed to a known set of crew members. */
    @Query("""
            select a.crewMemberId, a.missionId from AssignmentEntity a
            where a.organisationId = :organisationId
              and a.status = :status
              and a.crewMemberId in :crewMemberIds
            """)
    List<Object[]> crewAndMissionsFor(@Param("organisationId") UUID organisationId,
                                      @Param("crewMemberIds") Collection<UUID> crewMemberIds,
                                      @Param("status") AssignmentStatus status);

    /**
     * Withdraws every outstanding offer on a mission - FR-8 and BR-8.
     *
     * <p>One statement rather than loading the rows and calling {@code withdraw} on each. This runs
     * inside the transaction that is closing the mission, which already holds that mission's write
     * lock, so there is nothing to race with and nothing a per-row transition check could catch
     * that the {@code status = OFFERED} predicate does not.
     *
     * <p>{@code ACCEPTED} rows are untouched, deliberately. They are the crew member's history, and
     * withdrawing them would erase exactly the rows that history is derived from.
     *
     * @return how many offers were withdrawn, so the listener can say so in a log line
     */
    @Modifying
    @Query("""
            update AssignmentEntity a
            set a.status = :withdrawn, a.respondedAt = :now
            where a.missionId = :missionId
              and a.organisationId = :organisationId
              and a.status = :offered
            """)
    int withdrawOutstandingOffers(@Param("missionId") UUID missionId,
                                  @Param("organisationId") UUID organisationId,
                                  @Param("offered") AssignmentStatus offered,
                                  @Param("withdrawn") AssignmentStatus withdrawn,
                                  @Param("now") Instant now);
}
