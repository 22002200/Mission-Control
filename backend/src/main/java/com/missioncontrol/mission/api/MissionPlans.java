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

    /**
     * The same view, with a write lock held on the mission row for the rest of the transaction.
     *
     * <p>For a command rather than a read. Feature 07 offers and withdraws places through this, and
     * the lock is what makes invariant A2's cap hold under load: two leads filling the last seat on
     * a requirement serialise on the mission row, so the loser counts what the winner committed
     * instead of counting alongside it.
     *
     * <p>Identical access rules to {@link #forStaffing} - deliberately, and reusing the same beans
     * rather than restating them. The finer split feature 07 needs on top, that offering is the
     * owning lead's alone while a director may only read, is BR-9 and belongs to the caller: this
     * module's rule is owner-or-director and it has no opinion about which staffing verb is being
     * attempted. {@link MissionPlan#isOwnedBy} is there for exactly that check.
     *
     * <p>Takes the lock before it reads the detail, which is the order
     * {@code MissionRepository.lockByIdAndOrganisationId} explains at length. A caller must
     * therefore reach this before touching the same mission any other way in the transaction.
     *
     * @throws RuntimeException the same 404 and 403 {@link #forStaffing} raises, for the same
     *         reasons and in the same cases.
     */
    MissionPlan forStaffingUpdate(UUID missionId);
}
