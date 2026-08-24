/**
 * The org-scoped skill catalogue: the controlled vocabulary crew profiles rate themselves against
 * and mission requirements ask for.
 *
 * <p>It gets a module of its own so {@code mission} does not have to depend on {@code crew} merely
 * to name a skill. Nothing here knows what a mission or a crew member is, and that is the point.
 *
 * <p>Closed, and everything lives in {@code internal}. There is no {@code api} package yet because
 * no other module has asked for a type from this one. When {@code crew} needs to validate a skill
 * id, that is the moment to publish a lookup interface - not before.
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
