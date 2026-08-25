package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionWindow;
import java.time.Instant;

/**
 * When a mission happens, relative to now - FR-9's second filter.
 *
 * <p>Measured against the <strong>mission's dates</strong> and not its status. A mission nobody
 * remembered to close should still read as finished, and a crew member asking what is next means
 * the calendar rather than the workflow. The two usually agree; where they do not, the dates are
 * the honest answer.
 *
 * <p>Not stored anywhere and not an enum on any row. It is a question asked of a window at the
 * moment of the request, which is also why it cannot be a database predicate here: the dates
 * belong to {@code mission}, and this module holds no copy of them to filter on.
 *
 * <p>The three are exhaustive and do not overlap, so an unfiltered list is exactly the three
 * concatenated. Boundaries fall in {@code CURRENT}: a mission starting at this very instant has
 * started.
 */
enum Timeframe {

    /** Running now: started on or before this instant, and not yet ended. */
    CURRENT,

    /** Not started yet. */
    UPCOMING,

    /** Over. */
    PAST;

    boolean matches(MissionWindow mission, Instant now) {
        return switch (this) {
            case CURRENT -> !mission.startsAt().isAfter(now) && !mission.endsAt().isBefore(now);
            case UPCOMING -> mission.startsAt().isAfter(now);
            case PAST -> mission.endsAt().isBefore(now);
        };
    }
}
