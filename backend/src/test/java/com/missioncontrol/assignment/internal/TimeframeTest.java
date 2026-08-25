package com.missioncontrol.assignment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.mission.api.MissionWindow;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-9's second filter, and the boundaries the spec had to be pinned down on.
 *
 * <p>The definition was missing from the spec until this feature: {@code timeframe} is measured
 * against the mission's dates and not its status, so a mission nobody remembered to close still
 * reads as past. Worth testing because the three have to partition the set exactly - a mission
 * falling into two of them would appear twice in an unfiltered client, and one falling into none
 * would vanish.
 */
class TimeframeTest {

    private static final Instant STARTS = Instant.parse("2026-10-14T03:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-11T21:00:00Z");

    @Test
    @DisplayName("A mission running right now is CURRENT and nothing else")
    void running() {
        assertOnly(Timeframe.CURRENT, Instant.parse("2026-10-20T00:00:00Z"));
    }

    @Test
    @DisplayName("A mission that has not started is UPCOMING and nothing else")
    void notYetStarted() {
        assertOnly(Timeframe.UPCOMING, Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("A mission that has ended is PAST and nothing else")
    void over() {
        assertOnly(Timeframe.PAST, Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Both boundaries fall inside CURRENT")
    void boundariesAreCurrent() {
        // A mission starting at this very instant has started, and one ending at this very instant
        // has not yet finished. Putting either boundary in the neighbouring bucket would make a
        // mission flicker out of the crew member's list a moment before anything changed.
        assertOnly(Timeframe.CURRENT, STARTS);
        assertOnly(Timeframe.CURRENT, ENDS);
    }

    @Test
    @DisplayName("A closed mission is still judged on its dates, not its status")
    void statusIsIrrelevant() {
        MissionWindow aborted = window(MissionStatus.CLOSED);

        // Deliberate. A mission aborted before it flew is still upcoming by the calendar, and the
        // crew member reading their list wants to know when it was meant to happen. The status
        // chip beside it says it was closed.
        assertThat(Timeframe.UPCOMING.matches(aborted, Instant.parse("2026-09-01T00:00:00Z")))
                .isTrue();
    }

    /** Exactly one of the three matches, which is what makes them a partition. */
    private static void assertOnly(Timeframe expected, Instant now) {
        MissionWindow mission = window(MissionStatus.APPROVED);

        assertThat(Arrays.stream(Timeframe.values()).filter(t -> t.matches(mission, now)))
                .describedAs("at %s", now)
                .containsExactly(expected);
    }

    private static MissionWindow window(MissionStatus status) {
        return new MissionWindow(UUID.randomUUID(), UUID.randomUUID(), "Perihelion Watch", status,
                UUID.randomUUID(), STARTS, ENDS, false);
    }
}
