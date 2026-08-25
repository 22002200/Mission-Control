package com.missioncontrol.mission.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A mission reduced to the facts another module needs in order to reason about who is on it.
 *
 * <p>Deliberately thinner than {@link MissionPlan}: no requirements and no staffing counts. This is
 * what a bulk lookup can afford to return for a page of assignments, where fetching every
 * requirement of every mission would be the fan-out feature 07's NFR-4 forbids.
 *
 * <p>{@code completed} rather than a close reason. {@code assignment} needs exactly one thing from
 * how a mission ended - whether it counts as history, which the data model defines as closed as
 * {@code COMPLETED} - and publishing {@code MissionCloseReason} to answer a yes-or-no question
 * would put a second enum across a module boundary for no gain.
 *
 * @param id             the mission
 * @param organisationId the tenant this read was scoped to
 * @param name           as the mission is displayed
 * @param status         where it is in its lifecycle
 * @param missionLeadId  the owning user - who may offer and withdraw places on it
 * @param startsAt       inclusive start of the mission window, UTC
 * @param endsAt         end of the mission window, UTC, always after startsAt - M1
 * @param completed      closed, and closed as {@code COMPLETED} rather than aborted or rejected
 */
public record MissionWindow(UUID id, UUID organisationId, String name, MissionStatus status,
                            UUID missionLeadId, Instant startsAt, Instant endsAt,
                            boolean completed) {

    /**
     * Whether this mission's dates collide with the window given - invariant A3's arithmetic.
     *
     * <p>Half-open at both ends: a mission ending at exactly the instant another starts does not
     * overlap it. Back-to-back missions are a normal plan, and treating the shared boundary as a
     * clash would refuse an acceptance nobody would call a conflict.
     */
    public boolean overlaps(Instant from, Instant to) {
        return startsAt.isBefore(to) && from.isBefore(endsAt);
    }

    /**
     * Whether this mission still occupies its crew's calendar.
     *
     * <p>A closed mission does not - invariant A8 - which is what frees a crew member the moment an
     * aborted mission is closed rather than leaving them booked for dates nothing will happen on.
     */
    public boolean occupiesCalendar() {
        return status != MissionStatus.CLOSED;
    }

    /** Offers may only be made while the mission is {@code APPROVED} - invariant A1. */
    public boolean acceptsOffers() {
        return status == MissionStatus.APPROVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return missionLeadId.equals(userId);
    }
}
