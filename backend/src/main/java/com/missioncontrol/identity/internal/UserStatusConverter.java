package com.missioncontrol.identity.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link UserStatus} to the {@code SMALLINT} the {@code status} column holds. */
@Converter(autoApply = true)
class UserStatusConverter implements AttributeConverter<UserStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : (short) attribute.code();
    }

    @Override
    public UserStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : UserStatus.fromCode(dbData);
    }
}
