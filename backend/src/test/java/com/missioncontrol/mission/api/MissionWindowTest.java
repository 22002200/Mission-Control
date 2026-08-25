package com.missioncontrol.mission.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The arithmetic behind invariant A3, on its own.
 *
 * <p>Worth its own test rather than being left to the acceptance path, because an off-by-one in
 * {@code overlaps} is invisible in every ordinary case and wrong in exactly the two that matter:
 * back-to-back missions, and a mission wholly containing another. Both are normal plans, and a
 * schedule check that refused the first would be maddening in a way nobody would immediately trace
 * to a comparison operator.
 */
class MissionWindowTest {

    private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("A mission ending exactly as another starts does not overlap it")
    void backToBackIsNotAClash() {
        // Half-open at both ends. Flying home on the day the next crew rotation begins is a normal
        // plan; treating the shared boundary as a conflict would refuse an acceptance nobody would
        // call a clash.
        assertThat(window().overlaps(ENDS, Instant.parse("2026-11-30T00:00:00Z"))).isFalse();
        assertThat(window().overlaps(Instant.parse("2026-09-01T00:00:00Z"), STARTS)).isFalse();
    }

    @Test
    @DisplayName("A single shared day is an overlap")
    void oneDayOfContactIsAClash() {
        assertThat(window().overlaps(
                Instant.parse("2026-10-30T00:00:00Z"), Instant.parse("2026-11-30T00:00:00Z")))
                .isTrue();
    }

    @Test
    @DisplayName("A mission entirely inside another overlaps it, and the reverse")
    void containmentBothWays() {
        assertThat(window().overlaps(
                Instant.parse("2026-10-10T00:00:00Z"), Instant.parse("2026-10-20T00:00:00Z")))
                .isTrue();
        assertThat(window().overlaps(
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z")))
                .isTrue();
    }

    @Test
    @DisplayName("Two missions nowhere near each other do not overlap")
    void disjoint() {
        assertThat(window().overlaps(
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z")))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(MissionStatus.class)
    @DisplayName("Only a closed mission stops occupying the calendar - A8")
    void onlyClosedFreesTheCalendar(MissionStatus status) {
        // This is what makes aborting a mission release its crew immediately: a closed mission
        // blocks nothing, whatever its dates say.
        assertThat(window(status).occupiesCalendar()).isEqualTo(status != MissionStatus.CLOSED);
    }

    @ParameterizedTest
    @EnumSource(MissionStatus.class)
    @DisplayName("Only an APPROVED mission takes offers - A1")
    void onlyApprovedTakesOffers(MissionStatus status) {
        // Not ACTIVE. A mission already flying is not taking on crew, and a seat vacated after
        // launch is dealt with by editing the plan, which sends it back to PLAN under M5.
        assertThat(window(status).acceptsOffers()).isEqualTo(status == MissionStatus.APPROVED);
    }

    private static MissionWindow window() {
        return window(MissionStatus.APPROVED);
    }

    private static MissionWindow window(MissionStatus status) {
        return new MissionWindow(UUID.randomUUID(), UUID.randomUUID(), "Perihelion Watch", status,
                UUID.randomUUID(), STARTS, ENDS, false);
    }
}
