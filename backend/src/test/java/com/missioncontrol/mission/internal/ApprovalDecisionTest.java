package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The pinned codes behind {@link ApprovalDecision}.
 *
 * <p>The expected codes are restated here as a literal map rather than read off the enum, for the
 * same reason {@code MissionStatusTest} restates the transition table: a test that derives its
 * expectation from the thing it is testing agrees with any renumbering, which is precisely the
 * mistake the append-only rule exists to prevent. The integer is what is stored, so moving one
 * silently re-points every existing row.
 */
class ApprovalDecisionTest {

    private static final Map<ApprovalDecision, Integer> PINNED = Map.of(
            ApprovalDecision.PENDING, 1,
            ApprovalDecision.APPROVED, 2,
            ApprovalDecision.REJECTED, 3,
            ApprovalDecision.CANCELLED, 4);

    @ParameterizedTest
    @EnumSource(ApprovalDecision.class)
    void everyDecisionKeepsItsPinnedCode(ApprovalDecision decision) {
        assertThat(decision.code()).isEqualTo(PINNED.get(decision));
    }

    @ParameterizedTest
    @EnumSource(ApprovalDecision.class)
    void codesResolveBackToTheirDecision(ApprovalDecision decision) {
        assertThat(ApprovalDecision.fromCode(decision.code())).isSameAs(decision);
    }

    @Test
    void everyDecisionIsPinned() {
        // Catches the case the two tests above cannot: a new constant added without a code here.
        assertThat(PINNED).hasSize(ApprovalDecision.values().length);
    }

    @Test
    void anUnknownCodeFailsLoudly() {
        assertThatThrownBy(() -> ApprovalDecision.fromCode(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }
}
