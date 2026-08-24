package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The two attribute converters.
 *
 * <p>Small, but the null handling is load-bearing in one direction: {@code closeReason} is null on
 * every mission that is not closed, so a converter that turned null into a code - or threw on one -
 * would break half of invariant M4 the first time anyone saved a mission in PLAN.
 */
class MissionEnumConverterTest {

    private final MissionStatusConverter statuses = new MissionStatusConverter();
    private final MissionCloseReasonConverter reasons = new MissionCloseReasonConverter();

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

    @Test
    void nullsPassStraightThroughInBothDirections() {
        assertThat(statuses.convertToDatabaseColumn(null)).isNull();
        assertThat(statuses.convertToEntityAttribute(null)).isNull();
        assertThat(reasons.convertToDatabaseColumn(null)).isNull();
        assertThat(reasons.convertToEntityAttribute(null)).isNull();
    }
}
