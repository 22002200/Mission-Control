package com.missioncontrol.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the pinned enum codes.
 *
 * <p>These assertions look trivial and are not. The integer is what is stored, so if someone
 * reorders the constants or inserts one in the middle, every existing row silently means a
 * different role. This test is what turns that from a latent data corruption into a build failure.
 */
class UserRoleTest {

    @Test
    void codesArePinnedToTheValuesInTheDataModel() {
        assertThat(UserRole.DIRECTOR.code()).isEqualTo(1);
        assertThat(UserRole.MISSION_LEAD.code()).isEqualTo(2);
        assertThat(UserRole.CREW_MEMBER.code()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void fromCodeRoundTripsEveryRole(UserRole role) {
        assertThat(UserRole.fromCode(role.code())).isEqualTo(role);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, -1, 99})
    void unknownCodeIsRejectedRatherThanReturningNull(int code) {
        assertThatThrownBy(() -> UserRole.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown UserRole code");
    }

    @Test
    void hasExactlyTheThreeRolesTheProductDefines() {
        assertThat(UserRole.values()).containsExactly(
                UserRole.DIRECTOR, UserRole.MISSION_LEAD, UserRole.CREW_MEMBER);
    }
}
