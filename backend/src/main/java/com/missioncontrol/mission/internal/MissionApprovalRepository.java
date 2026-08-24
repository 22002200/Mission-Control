package com.missioncontrol.mission.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Approval cycles, always read by mission and always within one organisation.
 *
 * <p>The {@code organisationId} predicate is strictly redundant - the mission was already
 * tenant-scoped when it was loaded - and it is here anyway, for the same reason
 * {@link MissionRepository} carries it on every query. Invariant T1 says <em>every</em> query is
 * filtered by the caller's organisation, and a query that is only safe because of what its one
 * caller happens to do first is a query that becomes unsafe the day it gets a second caller.
 *
 * <p>No pessimistic lock here. The mission row is the single serialisation point for every
 * transition; taking a second lock on a second table invites deadlocks for no gain, and the
 * partial unique index behind M8 is the backstop.
 */
interface MissionApprovalRepository extends JpaRepository<MissionApprovalEntity, UUID> {

    Optional<MissionApprovalEntity> findByMissionIdAndOrganisationIdAndDecision(
            UUID missionId, UUID organisationId, ApprovalDecision decision);

    /**
     * The whole history, newest first - FR-6.
     *
     * <p>{@code id desc} is not decoration. Two cycles can share a {@code submittedAt}, and under
     * a fixed clock in a test they always do; without a tiebreak, 'the history holds two cycles in
     * order' is a nondeterministic assertion. Same reasoning as the {@code m.id asc} in
     * {@link MissionRepository#findForOrganisation}.
     */
    @Query("""
            select a from MissionApprovalEntity a
            where a.missionId = :missionId and a.organisationId = :organisationId
            order by a.submittedAt desc, a.id desc
            """)
    List<MissionApprovalEntity> findHistory(@Param("missionId") UUID missionId,
                                            @Param("organisationId") UUID organisationId);
}
