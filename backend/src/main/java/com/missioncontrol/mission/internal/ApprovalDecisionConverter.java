package com.missioncontrol.mission.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link ApprovalDecision} to the {@code SMALLINT} the {@code decision} column holds.
 *
 * <p>{@code Short}, not {@code Integer}: the column is {@code SMALLINT} and Hibernate's schema
 * validation compares the mapped JDBC type against the real one.
 */
@Converter(autoApply = true)
class ApprovalDecisionConverter implements AttributeConverter<ApprovalDecision, Short> {

    @Override
    public Short convertToDatabaseColumn(ApprovalDecision attribute) {
        return attribute == null ? null : (short) attribute.code();
    }

    @Override
    public ApprovalDecision convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ApprovalDecision.fromCode(dbData);
    }
}
