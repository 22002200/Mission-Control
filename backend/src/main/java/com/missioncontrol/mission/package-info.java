/**
 * Missions, the crew they call for, and the parts of their lifecycle that do not need a director.
 *
 * <p>Owns {@code Mission}, {@code CrewRequirement} and {@code RequiredSkill}. {@code MissionApproval}
 * belongs here too and arrives with feature 05.
 *
 * <p>The dependency that must never exist is on {@code assignment}. That module needs a mission's
 * dates and status in order to offer a place on it, so the arrow points that way and a second one
 * back would be a cycle. Staffing figures still have to reach a mission response, so this module
 * declares the shape it wants - {@link com.missioncontrol.mission.api.StaffingReadModel} - and
 * {@code assignment} will supply an implementation. The interface lives on the consumer's side
 * precisely so the compile-time arrow keeps pointing the right way; until feature 07 there is a
 * no-op that reports nothing as staffed.
 *
 * <p>{@code skill} and {@code identity} are on the allow-list for their published lookups: a
 * required skill stores a {@code skillId} and a mission stores a {@code missionLeadId}, and
 * neither can be rendered from the id alone.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Mission",
        allowedDependencies = {"platform", "shared", "skill :: api", "identity :: api"}
)
package com.missioncontrol.mission;
