/**
 * Offering a place on a mission, and what the crew member does about it.
 *
 * <p>Owns {@code Assignment}, and it is the only entity here. Everything else people ask of this
 * module - is somebody available, how many places are filled, what has this person flown - is
 * derived from those rows rather than stored beside them, because a stored copy is a second source
 * of truth that drifts.
 *
 * <p>The arrows all point out of here, which is the whole reason the module could be built last.
 * {@code mission} needs staffing counts and {@code matching} needs crew load, and both declared the
 * shape they wanted on their own side - {@link com.missioncontrol.mission.api.StaffingReadModel}
 * and {@link com.missioncontrol.matching.api.CrewLoadReadModel} - so implementing them here adds a
 * dependency from this module to those and not one back. That is not a stylistic preference: the
 * reverse is the cycle {@code ModularityTests} exists to catch, and it was foreseen in both
 * modules' documentation long before this one existed.
 *
 * <p>Nothing is published. No module depends on this one at compile time, and it does not
 * anticipate one: feature 08's dashboard will be the first consumer, and an {@code api} package
 * added the day it needs something is cheaper than a guess made now. The two ports above are how
 * data leaves, and they belong to their consumers.
 *
 * <p>Mission closure arrives as {@link com.missioncontrol.mission.api.MissionClosedEvent} rather
 * than as a call. Withdrawing the offers on a closed mission is a write, so no read model could
 * carry it, and a method on this module would have meant {@code mission} naming it.
 *
 * <p>{@code shared} is on the allow-list because BR-9 splits by role: withdrawing is the owning
 * mission lead's and accepting is the crew member's, and the endpoints say so with
 * {@code PreAuthorize} before any row is loaded.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Assignment",
        allowedDependencies = {
                "platform",
                "shared",
                "mission :: api",
                "crew :: api",
                "identity :: api",
                "matching :: api"
        }
)
package com.missioncontrol.assignment;
