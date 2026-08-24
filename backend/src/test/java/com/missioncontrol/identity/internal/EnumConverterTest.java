package com.missioncontrol.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.missioncontrol.shared.UserRole;
import org.junit.jupiter.api.Test;

/** The JPA converters are the only thing standing between the pinned codes and the columns. */
class EnumConverterTest {

    private final UserRoleConverter roles = new UserRoleConverter();
    private final UserStatusConverter statuses = new UserStatusConverter();

    @Test
    void rolesConvertBothWays() {
        assertThat(roles.convertToDatabaseColumn(UserRole.MISSION_LEAD)).isEqualTo((short) 2);
        assertThat(roles.convertToEntityAttribute((short) 2)).isEqualTo(UserRole.MISSION_LEAD);
    }

    @Test
    void statusesConvertBothWays() {
        assertThat(statuses.convertToDatabaseColumn(UserStatus.DISABLED)).isEqualTo((short) 2);
        assertThat(statuses.convertToEntityAttribute((short) 2)).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void nullsPassThroughUntouched() {
        assertThat(roles.convertToDatabaseColumn(null)).isNull();
        assertThat(roles.convertToEntityAttribute(null)).isNull();
        assertThat(statuses.convertToDatabaseColumn(null)).isNull();
        assertThat(statuses.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void anUnmappedColumnValueFailsLoudlyRatherThanBecomingNull() {
        assertThatThrownBy(() -> roles.convertToEntityAttribute((short) 9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> statuses.convertToEntityAttribute((short) 9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
