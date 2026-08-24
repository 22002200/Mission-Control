package com.missioncontrol.skill.api;

import java.util.UUID;

/**
 * A skill as another module sees it: enough to name it and to decide whether it may still be
 * chosen.
 *
 * <p>Deliberately smaller than the module's own {@code SkillResponse}. Category and description
 * are presentation concerns of the catalogue itself; nothing outside this module has needed them.
 *
 * <p>{@code active} is here rather than being filtered out by the lookup because the two callers
 * want opposite things. Validating a new mission requirement must reject a retired skill, while
 * rendering an existing one must still show its name - invariant S2 keeps retired skills readable
 * precisely so old requirements do not turn into blank rows.
 *
 * @param id     the catalogue entry
 * @param name   as the owning organisation spells it
 * @param active false once the skill has been retired
 */
public record SkillSummary(UUID id, String name, boolean active) {
}
