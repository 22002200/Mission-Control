package com.missioncontrol.assignment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Invariant A7, exhaustively, and the pinned codes.
 *
 * <p>Every cell rather than the handful the endpoints happen to use, the same treatment
 * {@code MissionStatusTest} gives M3. The transitions are cited by number in the spec, and the one
 * that is easiest to get wrong - {@code ACCEPTED} to {@code WITHDRAWN} being legal while
 * {@code ACCEPTED} to {@code DECLINED} is not - has nothing to do with who is asking, so it can be
 * settled here without a mission or a caller in sight.
 */
class AssignmentStatusTest {

    /**
     * Invariant A7 restated independently of the implementation.
     *
     * <p>Written out here rather than derived from the enum, on purpose. A test that asks the
     * production code what the rules are cannot notice when the rules change.
     */
    private static final Map<AssignmentStatus, Set<AssignmentStatus>> ALLOWED = Map.of(
            AssignmentStatus.OFFERED,
            Set.of(AssignmentStatus.ACCEPTED, AssignmentStatus.DECLINED, AssignmentStatus.WITHDRAWN),
            AssignmentStatus.ACCEPTED,
            Set.of(AssignmentStatus.WITHDRAWN),
            AssignmentStatus.DECLINED,
            Set.of(),
            AssignmentStatus.WITHDRAWN,
            Set.of());

    @Test
    @DisplayName("Every one of the 16 transitions matches the table in data-model.md")
    void transitionTableIsExact() {
        for (AssignmentStatus from : AssignmentStatus.values()) {
            for (AssignmentStatus to : AssignmentStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .describedAs("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    @DisplayName("An accepted place can be withdrawn but not declined")
    void acceptedCannotBeDeclined() {
        // The asymmetry is the rule, not an oversight. Once a crew member has accepted they are
        // assigned; letting them decline afterwards would be a self-service withdrawal, and BR-9
        // makes releasing somebody the owning mission lead's decision.
        assertThat(AssignmentStatus.ACCEPTED.canTransitionTo(AssignmentStatus.WITHDRAWN)).isTrue();
        assertThat(AssignmentStatus.ACCEPTED.canTransitionTo(AssignmentStatus.DECLINED)).isFalse();
    }

    @Test
    @DisplayName("A declined offer cannot be accepted later")
    void declinedIsFinal() {
        assertThat(AssignmentStatus.DECLINED.canTransitionTo(AssignmentStatus.ACCEPTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AssignmentStatus.class)
    @DisplayName("No status can transition to itself")
    void noSelfTransitions(AssignmentStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    @DisplayName("Declined and withdrawn are the terminal pair")
    void terminalStatuses() {
        assertThat(EnumSet.allOf(AssignmentStatus.class).stream()
                .filter(AssignmentStatus::isTerminal))
                .containsExactlyInAnyOrder(AssignmentStatus.DECLINED, AssignmentStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Offered and accepted are what count against a requirement - A2")
    void seatsAreOccupiedByBothOpenStates() {
        // An offer reserves the seat even though A4 says it reserves nobody's calendar. Without
        // that, a lead could offer one place to the whole roster and find out later that four
        // people accepted it.
        assertThat(EnumSet.allOf(AssignmentStatus.class).stream()
                .filter(AssignmentStatus::occupiesSeat))
                .containsExactlyInAnyOrder(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(AssignmentStatus.class)
    @DisplayName("Every code round-trips, and the codes are the ones the data model pins")
    void codesRoundTrip(AssignmentStatus status) {
        assertThat(AssignmentStatus.fromCode(status.code())).isSameAs(status);
    }

    @Test
    @DisplayName("The pinned codes are exactly 1 to 4, in the documented order")
    void pinnedCodes() {
        // Spelled out rather than derived. The integer is what is stored, so a reordering that
        // this test did not notice would silently re-point every existing row.
        assertThat(AssignmentStatus.OFFERED.code()).isEqualTo(1);
        assertThat(AssignmentStatus.ACCEPTED.code()).isEqualTo(2);
        assertThat(AssignmentStatus.DECLINED.code()).isEqualTo(3);
        assertThat(AssignmentStatus.WITHDRAWN.code()).isEqualTo(4);
    }

    @Test
    @DisplayName("An unknown code fails loudly rather than resolving to null")
    void unknownCodeThrows() {
        assertThatThrownBy(() -> AssignmentStatus.fromCode(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("There is no COMPLETED status - history is derived from the mission")
    void noCompletedStatus() {
        // A crew member's history is accepted assignments on missions closed as COMPLETED. A
        // fourth terminal state here would be a second place to record that, and a second place
        // for it to disagree with the mission.
        assertThat(EnumSet.allOf(AssignmentStatus.class))
                .extracting(Enum::name)
                .doesNotContain("COMPLETED");
    }

    @Test
    @DisplayName("The converter round-trips every status through the SMALLINT column")
    void converterRoundTrips() {
        AssignmentStatusConverter converter = new AssignmentStatusConverter();

        for (AssignmentStatus status : AssignmentStatus.values()) {
            Short stored = converter.convertToDatabaseColumn(status);
            assertThat(stored).isEqualTo((short) status.code());
            assertThat(converter.convertToEntityAttribute(stored)).isSameAs(status);
        }

        // Null in both directions. status is non-null on every row, so this is defensive rather
        // than load-bearing - but a converter that threw on null would turn a mapping mistake into
        // an error a long way from its cause.
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
