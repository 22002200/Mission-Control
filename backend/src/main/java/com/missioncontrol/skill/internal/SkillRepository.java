package com.missioncontrol.skill.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SkillRepository extends JpaRepository<SkillEntity, UUID> {

    /**
     * The tenant-scoped single read - invariant T1.
     *
     * <p>Scoped in the query rather than by fetching and then checking, so a skill in another
     * organisation is genuinely not found rather than found and rejected. That is what makes
     * BR-3 a 404 and not a 403.
     */
    Optional<SkillEntity> findByIdAndOrganisationId(UUID id, UUID organisationId);

    /**
     * The catalogue listing: FR-1 and FR-2 in one query.
     *
     * <p>Both filters are optional and a null means the filter is off, which keeps this to a
     * single query rather than four. Each parameter also appears in a typed comparison, so
     * Hibernate can infer its type - a parameter used only in a null check leaves PostgreSQL
     * unable to work out what it is.
     *
     * <p>{@code namePattern} arrives already lowercased, wildcarded and escaped; see
     * {@link SkillService}. Sorting is fixed here rather than exposed as a {@code sort} parameter,
     * because FR-1 specifies exactly one order and a sortable column is a promise about the
     * schema. {@code lower} makes the order case-insensitive, which is the order a person reading
     * a list expects.
     */
    @Query("""
            select s from SkillEntity s
            where s.organisationId = :organisationId
              and (:active is null or s.active = :active)
              and (:namePattern is null or lower(s.name) like :namePattern escape '\\')
            order by lower(s.name)
            """)
    Page<SkillEntity> findCatalogue(@Param("organisationId") UUID organisationId,
                                    @Param("active") Boolean active,
                                    @Param("namePattern") String namePattern,
                                    Pageable pageable);
}
