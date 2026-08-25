package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
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
 * Invariant M3, exhaustively, and the pinned codes.
 *
 * <p>The transition table is the one rule in this module that three separate specs cite by number,
 * so it is worth testing every cell rather than the handful the endpoints happen to use. Feature
 * 05 adds the arrows this feature does not exercise, and this test already covers them.
 */
class MissionStatusTest {

    /**
     * Invariant M3 restated independently of the implementation.
     *
     * <p>Written out here rather than derived from the enum, on purpose. A test that asks the
     * production code what the rules are cannot notice when the rules change.
     */
    private static final Map<MissionStatus, Set<MissionStatus>> ALLOWED = Map.of(
            MissionStatus.PLAN,
            Set.of(MissionStatus.PENDING_APPROVAL, MissionStatus.CLOSED),
            MissionStatus.PENDING_APPROVAL,
            Set.of(MissionStatus.APPROVED, MissionStatus.REJECTED, MissionStatus.CLOSED),
            MissionStatus.REJECTED,
            Set.of(MissionStatus.PLAN, MissionStatus.CLOSED),
            MissionStatus.APPROVED,
            Set.of(MissionStatus.ACTIVE, MissionStatus.PLAN, MissionStatus.CLOSED),
            MissionStatus.ACTIVE,
            Set.of(MissionStatus.PLAN, MissionStatus.CLOSED),
            MissionStatus.CLOSED,
            Set.of());

    @Test
    @DisplayName("Every one of the 36 transitions matches the table in data-model.md")
    void everyTransitionMatchesTheSpecifiedTable() {
        for (MissionStatus from : MissionStatus.values()) {
            for (MissionStatus to : MissionStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    @DisplayName("CLOSED is terminal, and nothing else is")
    void onlyClosedIsTerminal() {
        for (MissionStatus status : MissionStatus.values()) {
            assertThat(status.isTerminal()).isEqualTo(status == MissionStatus.CLOSED);
        }
    }

    @Test
    @DisplayName("Closing is reachable from every non-terminal status - abort is not a status")
    void everyNonTerminalStatusCanClose() {
        EnumSet.complementOf(EnumSet.of(MissionStatus.CLOSED)).forEach(status ->
                assertThat(status.canTransitionTo(MissionStatus.CLOSED))
                        .as("%s -> CLOSED", status)
                        .isTrue());
    }

    @Test
    @DisplayName("No status may transition to itself")
    void noSelfTransitions() {
        for (MissionStatus status : MissionStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    /**
     * The codes are stored, so changing one silently re-points every existing mission at a
     * different state. Asserted as literals for exactly that reason.
     */
    @Test
    void statusCodesArePinned() {
        assertThat(MissionStatus.PLAN.code()).isEqualTo(1);
        assertThat(MissionStatus.PENDING_APPROVAL.code()).isEqualTo(2);
        assertThat(MissionStatus.APPROVED.code()).isEqualTo(3);
        assertThat(MissionStatus.REJECTED.code()).isEqualTo(4);
        assertThat(MissionStatus.ACTIVE.code()).isEqualTo(5);
        assertThat(MissionStatus.CLOSED.code()).isEqualTo(6);
    }

    @Test
    void closeReasonCodesArePinned() {
        assertThat(MissionCloseReason.COMPLETED.code()).isEqualTo(1);
        assertThat(MissionCloseReason.ABORTED.code()).isEqualTo(2);
        assertThat(MissionCloseReason.REJECTED.code()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(MissionStatus.class)
    void everyStatusRoundTripsThroughItsCode(MissionStatus status) {
        assertThat(MissionStatus.fromCode(status.code())).isSameAs(status);
    }

    @ParameterizedTest
    @EnumSource(MissionCloseReason.class)
    void everyCloseReasonRoundTripsThroughItsCode(MissionCloseReason reason) {
        assertThat(MissionCloseReason.fromCode(reason.code())).isSameAs(reason);
    }

    @Test
    @DisplayName("An unknown code fails loudly rather than resolving to null")
    void unknownCodesThrow() {
        assertThatThrownBy(() -> MissionStatus.fromCode(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown MissionStatus code");
        assertThatThrownBy(() -> MissionCloseReason.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown MissionCloseReason code");
    }
}
