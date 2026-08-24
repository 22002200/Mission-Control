package com.missioncontrol.skill.internal;

import com.missioncontrol.platform.CurrentUser;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the catalogue.
 *
 * <p>The organisation comes from {@link CurrentUser} on every call and from nowhere else - no
 * method here takes one as an argument, so there is no way for a controller to pass the wrong one.
 * That is invariant T1 made structural rather than remembered.
 */
@Service
class SkillService {

    private final SkillRepository skills;
    private final CurrentUser currentUser;

    SkillService(SkillRepository skills, CurrentUser currentUser) {
        this.skills = skills;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    SkillPage list(Boolean active, String search, int page, int size) {
        Page<SkillResponse> found = skills.findCatalogue(
                        currentUser.organisationId(),
                        active,
                        namePattern(search),
                        PageRequest.of(page, size))
                .map(SkillService::toResponse);

        return SkillPage.from(found);
    }

    @Transactional(readOnly = true)
    SkillResponse get(UUID id) {
        return skills.findByIdAndOrganisationId(id, currentUser.organisationId())
                .map(SkillService::toResponse)
                .orElseThrow(SkillNotFoundException::new);
    }

    /**
     * Turns a search term into a LIKE pattern, or null when there is nothing to search for.
     *
     * <p>The wildcards are escaped before the surrounding ones are added. Without that, a search
     * for an underscore matches every single character and a search for a percent sign matches the
     * entire catalogue - a filter that silently ignores what it was given is worse than one that
     * rejects it.
     *
     * <p>Lowercased here rather than in the query so the database is not asked to call
     * {@code lower} on a constant for every row.
     */
    private static String namePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    private static SkillResponse toResponse(SkillEntity skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getCategory(),
                skill.getDescription(),
                skill.isActive());
    }
}
