package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link MissionStatus} to the {@code SMALLINT} the {@code status} column holds.
 *
 * <p>{@code Short}, not {@code Integer}: the column is {@code SMALLINT} and Hibernate's schema
 * validation compares the mapped JDBC type against the real one.
 */
@Converter(autoApply = true)
class MissionStatusConverter implements AttributeConverter<MissionStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(MissionStatus attribute) {
        return attribute == null ? null : (short) attribute.code();
    }

    @Override
    public MissionStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : MissionStatus.fromCode(dbData);
    }
}
