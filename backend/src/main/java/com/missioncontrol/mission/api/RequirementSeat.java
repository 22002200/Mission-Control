package com.missioncontrol.mission.api;

import java.util.UUID;

/**
 * One staffing line reduced to its capacity and its name.
 *
 * <p>The counterpart of {@link MissionWindow}, and thinner than {@link RequirementPlan} for the
 * same reason: no skills, and above all no accepted or offered counts. Those come from
 * {@code assignment} through {@link StaffingReadModel}, and handing them back to the module that
 * supplied them would be a round trip that could only ever agree with itself.
 *
 * <p>{@code missionId} is here so a caller can reject a requirement paired with the wrong mission
 * rather than quietly staffing it. {@link MissionPlan#requirement} makes the same check the other
 * way round, and both answer 404: telling a caller that a requirement id is real but belongs
 * elsewhere is the leak the mission 404 exists to prevent.
 *
 * @param id            the requirement
 * @param missionId     the mission it belongs to
 * @param title         for example Flight Engineer
 * @param requiredCount seats in total, at least 1 - invariant M9
 */
public record RequirementSeat(UUID id, UUID missionId, String title, int requiredCount) {
}
