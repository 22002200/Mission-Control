package com.missioncontrol.mission.api;

import java.util.UUID;

/**
 * Reading a mission and its crew requirements, for a module that staffs missions but does not own
 * them.
 *
 * <p>Published because {@code matching} needs the mission window and every requirement's skills in
 * order to rank anyone, and feature 06's NFR-5 says it reads this module through a published
 * interface. Everything it would otherwise reach for - {@code MissionEntity},
 * {@code CrewRequirementEntity}, {@code MissionLoader} - is internal, and
 * {@code ModularityTests} fails the build on any attempt to touch them.
 *
 * <p><strong>Access is this module's job, not the caller's.</strong> The implementation applies the
 * same tenancy, visibility and owner-or-director rules the mission endpoints apply, using the same
 * beans, so there is exactly one place that decides what a caller may see. That is deliberate:
 * {@code MissionAccess} records what happened the one time two code paths disagreed, answering 404
 * on a read and 403 on a write for the same mission and telling the caller it existed.
 */
public interface MissionPlans {

    /**
     * The mission, with staffing counts folded in, for a caller entitled to run matching on it.
     *
     * <p>Reads the caller from the security context rather than taking a user - unlike the bulk
     * lookups published by {@code skill} and {@code identity}, which take an organisation. Those
     * answer a question about data; this one answers a question about permission, and a permission
     * check that accepts the identity it is checking as an argument is not much of a check.
     *
     * @throws RuntimeException an {@code ApiProblemException} carrying 404 when no such mission is
     *         visible to the caller - absent, another tenant's, or outside their visibility, which
     *         are deliberately indistinguishable - and 403 when they can see it but may not staff
     *         it, which is a crew member on its own crew.
     */
    MissionPlan forStaffing(UUID missionId);
}
