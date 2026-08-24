package com.missioncontrol.mission.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How many people have actually accepted, and which missions a given crew member is on.
 *
 * <p>Both are facts about assignments, which this module does not own and must not depend on.
 * Declaring the interface here instead - the consumer's side - is what keeps the compile-time
 * dependency pointing from {@code assignment} to {@code mission} and not back. The alternative,
 * publishing it from {@code assignment} and injecting that, is the cycle
 * {@code ModularityTests} exists to catch.
 *
 * <p>It is deliberately a read model and not a query into another module's tables. Nothing here
 * knows what an assignment is, only how many of them ended in an acceptance.
 *
 * <p>Until feature 07 the only implementation is {@code UnstaffedReadModel}, which answers nothing
 * for everything. That is honest rather than convenient: a mission with requirements reads as
 * unstaffed, and starting one is refused, which is correct - there is no way to crew it yet.
 */
public interface StaffingReadModel {

    /**
     * Accepted assignments per requirement.
     *
     * <p>Bulk because a mission has several requirements and a list page has several missions;
     * asking per requirement is the N+1 that feature 04's NFR-1 forbids.
     *
     * @return accepted counts keyed by requirement id. A requirement nobody has accepted may be
     *         absent rather than mapped to zero, so read it with a default.
     */
    Map<UUID, Integer> acceptedCountsByRequirement(Collection<UUID> requirementIds);

    /**
     * The missions a crew member holds an assignment on, in any state.
     *
     * <p>This is the whole of a crew member's mission visibility - FR-2. Offers count, not only
     * acceptances: being asked is reason enough to see what you are being asked to join.
     */
    Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId);
}
