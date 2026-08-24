package com.missioncontrol.mission.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CrewRequirementRepository extends JpaRepository<CrewRequirementEntity, UUID> {

    /**
     * A requirement, but only as part of the mission it was addressed through.
     *
     * <p>Matching on both ids at once is what stops a caller pairing a requirement id with someone
     * else's mission id to discover whether the requirement exists. The organisation is checked
     * too, so this is tenant-scoped in its own right rather than relying on the mission having
     * been fetched first.
     */
    @Query("""
            select r from CrewRequirementEntity r
            left join fetch r.requiredSkills
            where r.id = :id
              and r.mission.id = :missionId
              and r.organisationId = :organisationId
            """)
    Optional<CrewRequirementEntity> findOnMission(@Param("id") UUID id,
                                                  @Param("missionId") UUID missionId,
                                                  @Param("organisationId") UUID organisationId);
}
