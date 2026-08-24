package com.missioncontrol.matching.internal;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One eligible crew member, ranked, with the arithmetic that got them there.
 *
 * <p>No name. Names are resolved in bulk through {@code UserDirectory} once the candidate set is
 * settled, so scoring never touches {@code identity} and the engine stays a function of numbers.
 *
 * @param crewMemberId      the candidate
 * @param score             the final figure, already rounded to three decimals
 * @param skillScore        the weighted average across required skills, 0 to 1
 * @param experienceBonus   the capped positive term
 * @param completedMissions what produced it
 * @param loadPenalty       the capped negative term, held as a positive magnitude
 * @param recentAssignments what produced it
 * @param skills            per-skill detail, in a stable order
 */
record ScoredCandidate(UUID crewMemberId, double score, double skillScore, double experienceBonus,
                       int completedMissions, double loadPenalty, int recentAssignments,
                       List<SkillContribution> skills) {

    /**
     * Highest score first, then crew member id - BR-9.
     *
     * <p>The tie-break is not cosmetic. Ties are common: a requirement with one mandatory skill
     * puts everyone who sits exactly on the minimum on the same score, and without a second key the
     * order would depend on whatever order the roster query happened to return. Feature 06's NFR-1
     * requires two identical requests to produce identical output, and a mission lead watching the
     * list reshuffle between rematches would reasonably conclude the ranking meant nothing.
     *
     * <p>It compares the rounded score, which is the figure the caller is shown. Sorting on more
     * precision than is displayed produces an order the response cannot justify - two candidates
     * both reading 0.750, in an order nothing on screen explains.
     */
    static Comparator<ScoredCandidate> ranking() {
        return Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(ScoredCandidate::crewMemberId);
    }
}
