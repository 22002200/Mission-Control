package com.missioncontrol.matching.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What assignments say about a crew member: whether they are free, and how much they have been
 * asked to do.
 *
 * <p>A port, not an offer. Feature 07's {@code assignment} module will implement this; declaring it
 * here on the consumer's side is what keeps the compile-time arrow pointing from {@code assignment}
 * to {@code matching} rather than back. Publishing it from {@code assignment} instead would read
 * more naturally and would be the cycle. This is the same trade {@code mission.api.StaffingReadModel}
 * already makes, and for the same reason.
 *
 * <p>Every method is bulk and takes the whole candidate set. Feature 06's NFR-2 forbids a
 * per-candidate query outright, and a single-crew-member variant of any of these would make that
 * N+1 the easiest thing to write.
 *
 * <p>Until 07 the only implementation reports nothing: nobody is unavailable, nobody is already on
 * the mission, and every count is zero. That is honest rather than convenient - with no assignment
 * module there genuinely are no assignments, so matching ranks on skills and availability alone and
 * the answer is correct rather than provisional.
 */
public interface CrewLoadReadModel {

    /**
     * Crew who cannot take this mission because they are already committed elsewhere for it.
     *
     * <p>An {@code ACCEPTED} assignment, on a mission that is not {@code CLOSED}, whose dates
     * overlap the window - invariant A3. Offers do not count: A4 is explicit that an offer reserves
     * nobody, so two leads may legitimately be asking the same person for the same dates.
     *
     * <p>A closed mission does not occupy the calendar, which is why aborting one frees its crew
     * immediately - invariant A8.
     *
     * @param startsAt inclusive start of the mission window
     * @param endsAt   end of the mission window
     * @return crew member ids to exclude. Empty when nobody is committed.
     */
    Set<UUID> crewUnavailableBetween(UUID organisationId, Instant startsAt, Instant endsAt);

    /**
     * Crew who already hold an {@code OFFERED} or {@code ACCEPTED} assignment on this mission.
     *
     * <p>Invariant A5 allows at most one non-terminal assignment per person per mission, so
     * suggesting one of these again would produce an offer the server is bound to refuse.
     *
     * <p>Declined and withdrawn do not count. Both are terminal, and someone who turned a place
     * down is not barred from being asked again after the plan changes.
     */
    Set<UUID> crewAlreadyOnMission(UUID missionId);

    /**
     * How many missions each crew member has actually seen through.
     *
     * <p>{@code ACCEPTED} assignments on missions closed as {@code COMPLETED} - the definition of
     * assignment history in the data model. Aborted missions do not count towards experience,
     * because being assigned to something that never ran is not experience of it.
     *
     * @return counts keyed by crew member id. Someone with no history may be absent rather than
     *         mapped to zero, so read it with a default.
     */
    Map<UUID, Integer> completedMissionCounts(UUID organisationId, Collection<UUID> crewMemberIds);

    /**
     * How much each crew member is carrying right now.
     *
     * <p>{@code ACCEPTED} assignments whose mission starts on or after {@code since}. That single
     * comparison covers both halves of the rule feature 06 states - within the recent window, or in
     * the future - because anything ahead of now is necessarily ahead of a cutoff behind it. There
     * is no separate future clause and there does not need to be one.
     *
     * <p>The cutoff is the caller's to compute, and it is not a constant: it is derived from the
     * organisation's own median mission length, so a body running three-day sorties and one running
     * six-month expeditions are measured on their own timescales rather than a shared year.
     *
     * @param since the earliest mission start that still counts as recent
     * @return counts keyed by crew member id; absent means zero.
     */
    Map<UUID, Integer> recentAssignmentCounts(UUID organisationId, Collection<UUID> crewMemberIds,
                                              Instant since);
}
