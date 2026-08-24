package com.missioncontrol.matching.internal;

import java.util.UUID;

/**
 * What one required skill did to one candidate's score.
 *
 * <p>Carried so the breakdown can say which skills matched and which fell short - feature 06's
 * FR-4. Without it a score is a number the mission lead has to take on trust, and the whole point
 * of preferring the least over-qualified candidate is that it is surprising until it is explained.
 *
 * <p>No skill name. Names come from the {@code skill} catalogue in one bulk lookup at the edge, so
 * that a candidate scored against five skills does not cause five lookups.
 *
 * @param skillId      the catalogue entry
 * @param required     the requirement's minimum, 1 to 5
 * @param actual       what the candidate is rated at; 0 means they hold no rating for it
 * @param mandatory    true if this skill was also a hard filter
 * @param weight       the requirement's weighting for this skill
 * @param contribution the unweighted term this skill produced, 0 to 1 - {@code fit} for a
 *                     mandatory skill and {@code met} for a preferred one
 */
record SkillContribution(UUID skillId, int required, int actual, boolean mandatory, int weight,
                         double contribution) {

    /** A preferred skill held below its minimum, or not held at all - a shortfall, per FR-4. */
    boolean isShortfall() {
        return !mandatory && actual < required;
    }
}
