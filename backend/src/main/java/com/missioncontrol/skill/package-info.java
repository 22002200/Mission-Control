/**
 * The org-scoped skill catalogue: the controlled vocabulary crew profiles rate themselves against
 * and mission requirements ask for.
 *
 * <p>It gets a module of its own so {@code mission} does not have to depend on {@code crew} merely
 * to name a skill. Nothing here knows what a mission or a crew member is, and that is the point.
 *
 * <p>The {@code api} package holds exactly one interface,
 * {@link com.missioncontrol.skill.api.SkillCatalogue}, added when feature 04 gave a second module
 * a real reason to resolve a skill id - {@code mission} must reject a retired skill on a
 * requirement and print the name of one already chosen. Everything else stays in
 * {@code internal}, including the HTTP endpoints and the entity.
 *
 * <p>The allow-list names only {@code platform}, which is where {@code CurrentUser} comes from.
 * {@code shared} is deliberately absent: reading the catalogue is open to every role, so nothing
 * here needs to name a {@code UserRole}. The write endpoints will need it, and adding it then is
 * one line.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Skill",
        allowedDependencies = {"platform"}
)
package com.missioncontrol.skill;
