/**
 * Missions, the crew they call for, and their whole lifecycle - including a director's decision.
 *
 * <p>Owns {@code Mission}, {@code MissionApproval}, {@code CrewRequirement} and
 * {@code RequiredSkill}.
 *
 * <p>The dependency that must never exist is on {@code assignment}. That module needs a mission's
 * dates and status in order to offer a place on it, so the arrow points that way and a second one
 * back would be a cycle. Two things still have to travel the other way, and neither is a call.
 *
 * <p>Staffing figures reach a mission response through
 * {@link com.missioncontrol.mission.api.StaffingReadModel}, a shape this module declares and
 * {@code assignment} implements. The interface lives on the consumer's side precisely so the
 * compile-time arrow keeps pointing the right way, and {@code UnstaffedReadModel} remains as the
 * fallback for an application started without an assignment module at all.
 *
 * <p>Closing a mission has to withdraw its outstanding offers, which is a write rather than a
 * question, so a read model cannot carry it. That goes out as
 * {@link com.missioncontrol.mission.api.MissionClosedEvent} and this module never learns who
 * listened.
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
