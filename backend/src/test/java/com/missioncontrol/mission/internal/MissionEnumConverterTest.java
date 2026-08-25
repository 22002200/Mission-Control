package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The three attribute converters.
 *
 * <p>Small, but the null handling is load-bearing in one direction: {@code closeReason} is null on
 * every mission that is not closed, so a converter that turned null into a code - or threw on one -
 * would break half of invariant M4 the first time anyone saved a mission in PLAN.
 */
class MissionEnumConverterTest {

    private final MissionStatusConverter statuses = new MissionStatusConverter();
    private final MissionCloseReasonConverter reasons = new MissionCloseReasonConverter();
    private final ApprovalDecisionConverter decisions = new ApprovalDecisionConverter();

    @ParameterizedTest
    @EnumSource(MissionStatus.class)
    void statusRoundTripsThroughTheColumn(MissionStatus status) {
        Short stored = statuses.convertToDatabaseColumn(status);

        assertThat(stored).isEqualTo((short) status.code());
        assertThat(statuses.convertToEntityAttribute(stored)).isSameAs(status);
    }

    @ParameterizedTest
    @EnumSource(MissionCloseReason.class)
    void closeReasonRoundTripsThroughTheColumn(MissionCloseReason reason) {
        Short stored = reasons.convertToDatabaseColumn(reason);

        assertThat(stored).isEqualTo((short) reason.code());
        assertThat(reasons.convertToEntityAttribute(stored)).isSameAs(reason);
    }

    @ParameterizedTest
    @EnumSource(ApprovalDecision.class)
    void approvalDecisionRoundTripsThroughTheColumn(ApprovalDecision decision) {
        Short stored = decisions.convertToDatabaseColumn(decision);

        assertThat(stored).isEqualTo((short) decision.code());
        assertThat(decisions.convertToEntityAttribute(stored)).isSameAs(decision);
    }

    @Test
    void nullsPassStraightThroughInBothDirections() {
        assertThat(statuses.convertToDatabaseColumn(null)).isNull();
        assertThat(statuses.convertToEntityAttribute(null)).isNull();
        assertThat(reasons.convertToDatabaseColumn(null)).isNull();
        assertThat(reasons.convertToEntityAttribute(null)).isNull();
        // decision is NOT NULL in the schema, so this one never happens in practice. Asserted
        // anyway, because the alternative is a converter that throws on a value the other two
        // tolerate, and that asymmetry would only ever be discovered by accident.
        assertThat(decisions.convertToDatabaseColumn(null)).isNull();
        assertThat(decisions.convertToEntityAttribute(null)).isNull();
    }
}
