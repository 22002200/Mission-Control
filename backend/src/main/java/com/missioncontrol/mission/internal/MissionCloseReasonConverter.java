package com.missioncontrol.mission.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link MissionCloseReason} to its {@code SMALLINT} column.
 *
 * <p>Nulls pass straight through, and that is load-bearing: the reason is null for every mission
 * that is not closed, which is half of invariant M4.
 */
@Converter(autoApply = true)
class MissionCloseReasonConverter implements AttributeConverter<MissionCloseReason, Short> {

    @Override
    public Short convertToDatabaseColumn(MissionCloseReason attribute) {
        return attribute == null ? null : (short) attribute.code();
    }

    @Override
    public MissionCloseReason convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : MissionCloseReason.fromCode(dbData);
    }
}
