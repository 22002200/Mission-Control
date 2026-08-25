/**
 * What other modules may use from {@code mission}.
 *
 * <p>Three kinds of thing live here, and the distinction is worth keeping straight.
 *
 * <p><strong>A port this module asks for.</strong>
 * {@link com.missioncontrol.mission.api.StaffingReadModel} is implemented by {@code assignment},
 * which keeps the compile-time dependency pointing from {@code assignment} to {@code mission} and
 * not the other way. Declaring it from the consumer side is what makes that possible at all - the
 * reverse is the cycle {@code ModularityTests} exists to catch.
 *
 * <p><strong>Views this module offers.</strong> {@link com.missioncontrol.mission.api.MissionPlans}
 * answers whether a caller may staff a mission and hands back its requirements;
 * {@link com.missioncontrol.mission.api.MissionWindows} answers what a set of missions are, scoped
 * to an organisation and with no permission check. They are separate because their contracts are,
 * and the reasoning is on {@code MissionWindows} itself.
 *
 * <p><strong>An announcement.</strong>
 * {@link com.missioncontrol.mission.api.MissionClosedEvent} exists because closing a mission has to
 * withdraw its outstanding offers, which is a write this module may not perform and must not
 * request by name.
 *
 * <p>{@link com.missioncontrol.mission.api.MissionStatus} is here rather than in {@code internal}
 * because a crew member's own assignment list renders the status of each mission. That is the only
 * reason, and {@code MissionCloseReason} stayed behind precisely because nothing outside needs to
 * name it.
 */
@org.springframework.modulith.NamedInterface("api")
package com.missioncontrol.mission.api;
