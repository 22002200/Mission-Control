package com.missioncontrol.skill.internal;

import com.missioncontrol.skill.api.SkillCatalogue;
import com.missioncontrol.skill.api.SkillSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue's published lookup, implemented over the same repository the read endpoints use.
 *
 * <p>A separate bean from {@link SkillService} rather than a second role for it. The service
 * answers HTTP and returns {@code SkillResponse}; this answers other modules and returns
 * {@code SkillSummary}. Merging them would put a public interface on a class whose other methods
 * are an internal contract, and the two will drift.
 */
@Component
class SkillCatalogueLookup implements SkillCatalogue {

    private final SkillRepository skills;

    SkillCatalogueLookup(SkillRepository skills) {
        this.skills = skills;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillSummary> findByIds(Collection<UUID> skillIds, UUID organisationId) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }

        return skills.findAllByIdInAndOrganisationId(skillIds, organisationId).stream()
                .map(skill -> new SkillSummary(skill.getId(), skill.getName(), skill.isActive()))
                .collect(Collectors.toMap(SkillSummary::id, Function.identity()));
    }
}
