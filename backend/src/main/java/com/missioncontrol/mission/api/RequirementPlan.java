package com.missioncontrol.mission.api;

import java.util.List;
import java.util.UUID;

/**
 * One staffing line, with how much of it is already spoken for.
 *
 * <p>{@code acceptedCount} and {@code offeredCount} are not this module's data - they come from
 * {@code assignment} through {@link StaffingReadModel} and are folded in here so a caller gets one
 * coherent picture of a requirement rather than having to join two sources itself. Before feature
 * 07 both are zero, which is the correct answer rather than a stub: nobody can have been offered
 * anything yet.
 *
 * @param id            the requirement
 * @param title         for example Flight Engineer
 * @param requiredCount seats in total, at least 1 - invariant M9
 * @param acceptedCount how many have accepted
 * @param offeredCount  how many hold an outstanding offer
 * @param skills        what the line asks for; may be empty, which matches everyone
 */
public record RequirementPlan(UUID id, String title, int requiredCount, int acceptedCount,
                              int offeredCount, List<RequiredSkillSpec> skills) {

    /**
     * Seats still worth suggesting anyone for.
     *
     * <p>Floored at zero rather than allowed to go negative. Invariant A2 caps offered plus
     * accepted at {@code requiredCount} so it should not happen, but a read model that returns a
     * negative count of things to do would propagate into a list size and fail somewhere far less
     * obvious than here.
     */
    public int openSeats() {
        return Math.max(0, requiredCount - acceptedCount - offeredCount);
    }
}
