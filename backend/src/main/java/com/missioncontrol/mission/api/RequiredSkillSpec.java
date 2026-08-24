package com.missioncontrol.mission.api;

import java.util.UUID;

/**
 * One skill a requirement calls for, as the matching engine needs to see it.
 *
 * <p>Ids and numbers only - no skill name. Names live in the {@code skill} catalogue and are
 * resolved in bulk by whoever is rendering; putting one here would make every mission read pull the
 * catalogue whether or not anybody was going to print it.
 *
 * <p>{@code minimumProficiency} widens to {@code int} from the {@code short} the column stores.
 * That narrow type exists to match {@code SMALLINT} and has no business in the scoring arithmetic
 * on the other side of this boundary.
 *
 * @param skillId            the catalogue entry, resolvable through {@code SkillCatalogue}
 * @param minimumProficiency 1 to 5
 * @param mandatory          true makes this a hard filter as well as a scored term
 * @param weight             relative importance when ranking; at least 1
 */
public record RequiredSkillSpec(UUID skillId, int minimumProficiency, boolean mandatory,
                                int weight) {
}
