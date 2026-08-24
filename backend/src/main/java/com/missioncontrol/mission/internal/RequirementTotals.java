package com.missioncontrol.mission.internal;

import java.util.UUID;

/**
 * One requirement reduced to what a list card needs: which mission it belongs to, its own id so
 * staffing can be looked up against it, and how many people it asks for.
 *
 * @param missionId     the owning mission
 * @param requirementId used as the key into the staffing read model
 * @param requiredCount how many crew this line calls for
 */
record RequirementTotals(UUID missionId, UUID requirementId, int requiredCount) {
}
