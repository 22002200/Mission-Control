package com.missioncontrol.identity.internal;

import com.missioncontrol.shared.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link UserRole} to the {@code SMALLINT} the {@code role} column actually holds.
 *
 * <p>{@code Short}, not {@code Integer}: the column is {@code SMALLINT}, and Hibernate's schema
 * validation compares the mapped JDBC type against the real one.
 */
@Converter(autoApply = true)
class UserRoleConverter implements AttributeConverter<UserRole, Short> {

    @Override
    public Short convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : (short) attribute.code();
    }

    @Override
    public UserRole convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : UserRole.fromCode(dbData);
    }
}
