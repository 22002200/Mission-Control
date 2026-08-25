package com.missioncontrol.assignment.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link AssignmentStatus} to the {@code SMALLINT} the {@code status} column holds.
 *
 * <p>{@code Short}, not {@code Integer}: the column is {@code SMALLINT} and Hibernate's schema
 * validation compares the mapped JDBC type against the real one.
 *
 * <p>Storing the pinned code rather than {@code EnumType.STRING} or - far worse -
 * {@code EnumType.ORDINAL} is the rule the whole data model follows: an ordinal re-points every
 * existing row the moment somebody reorders a constant, and does it silently.
 */
@Converter
class AssignmentStatusConverter implements AttributeConverter<AssignmentStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(AssignmentStatus status) {
        return status == null ? null : (short) status.code();
    }

    @Override
    public AssignmentStatus convertToEntityAttribute(Short code) {
        return code == null ? null : AssignmentStatus.fromCode(code);
    }
}
