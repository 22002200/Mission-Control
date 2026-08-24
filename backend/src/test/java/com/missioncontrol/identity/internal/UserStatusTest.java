package com.missioncontrol.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** As {@code UserRoleTest}: the codes are stored, so they must never drift. */
class UserStatusTest {

    @Test
    void codesArePinnedToTheValuesInTheDataModel() {
        assertThat(UserStatus.ACTIVE.code()).isEqualTo(1);
        assertThat(UserStatus.DISABLED.code()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void fromCodeRoundTripsEveryStatus(UserStatus status) {
        assertThat(UserStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> UserStatus.fromCode(3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
