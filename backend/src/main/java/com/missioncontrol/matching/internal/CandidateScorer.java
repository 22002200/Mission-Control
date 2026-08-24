package com.missioncontrol.matching.internal;

import com.missioncontrol.crew.api.CrewProfile;
import com.missioncontrol.mission.api.RequiredSkillSpec;
import com.missioncontrol.mission.api.RequirementPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * How close a crew member is to what a requirement actually asked for - BR-5 through BR-9.
 *
 * <p>Pure: no database, no security context, no clock. Everything it needs arrives as an argument,
 * which is what lets the scoring rules be tested exhaustively without a Spring context. It is a
 * bean only so the configured constants can be injected once rather than threaded through every
 * call.
 *
 * <p>The counter-intuitive half is BR-5. A mandatory minimum is the job's real bar, and exceeding
 * it buys the mission nothing while costing the organisation something: put a 5 out of 5 specialist
 * on a requirement needing 3 and they are unavailable for the mission that genuinely needs a 5,
 * which the 3 could never have covered. Over-qualification is an opportunity cost, so it is scored
 * down. Preferred skills are the opposite - they are extras rather than a threshold, so more is
 * better and falling slightly short still earns something.
 */
@Component
class CandidateScorer {

    /** The rating scale crew are held on, and therefore the ceiling BR-5 measures excess against. */
    private static final int MAX_PROFICIENCY = 5;

    private static final int SCORE_SCALE = 3;

    private final MatchingProperties properties;

    CandidateScorer(MatchingProperties properties) {
        this.properties = properties;
    }

    /**
     * Scores one candidate against one requirement, or rejects them.
     *
     * @param load       how many recent or upcoming assignments they hold
     * @param completed  how many missions they have seen through
     * @return empty when a mandatory skill is not held at its minimum - BR-2, a hard filter. An
     *         excluded candidate is not ranked last, they are absent, because a mission lead
     *         reading a list of people who cannot do the job has been given work rather than an
     *         answer.
     */
    Optional<ScoredCandidate> score(RequirementPlan requirement, CrewProfile candidate,
                                    int completed, int load) {

        List<SkillContribution> contributions = new ArrayList<>();

        for (RequiredSkillSpec required : requirement.skills()) {
            int actual = candidate.proficiencyIn(required.skillId());

            if (required.mandatory() && actual < required.minimumProficiency()) {
                return Optional.empty();
            }

            contributions.add(new SkillContribution(
                    required.skillId(),
                    required.minimumProficiency(),
                    actual,
                    required.mandatory(),
                    required.weight(),
                    required.mandatory()
                            ? fit(actual, required.minimumProficiency())
                            : met(actual, required.minimumProficiency())));
        }

        double skillScore = weightedAverage(contributions);
        double experienceBonus = Math.min(
                completed * properties.experienceBonusPerMission(),
                properties.experienceBonusCap());
        double loadPenalty = Math.min(
                load * properties.loadPenaltyPerAssignment(),
                properties.loadPenaltyCap());

        return Optional.of(new ScoredCandidate(
                candidate.crewMemberId(),
                round(skillScore + experienceBonus - loadPenalty),
                round(skillScore),
                round(experienceBonus),
                completed,
                round(loadPenalty),
                load,
                List.copyOf(contributions)));
    }

    /**
     * BR-5, the mandatory term: 1.0 at exactly the minimum, falling to 0.0 at a perfect 5.
     *
     * <p>{@code excess} cannot be negative - BR-2 has already excluded anyone below the bar - so
     * the result never goes above 1.0.
     *
     * <p>A minimum of 5 makes the denominator zero, and the answer is 1.0 rather than a division by
     * zero. That is not a fudge: when the job needs the top of the scale, nobody can be
     * over-qualified for it, so everyone who clears the bar fits it exactly.
     */
    private static double fit(int actual, int minimum) {
        int headroom = MAX_PROFICIENCY - minimum;
        if (headroom == 0) {
            return 1.0;
        }
        return 1.0 - ((double) (actual - minimum) / headroom);
    }

    /**
     * BR-6, the preferred term: full credit at or above the minimum, pro-rata below it, nothing for
     * a skill the candidate does not hold at all.
     *
     * <p>The minimum is at least 1 by invariant M10, so the division is safe.
     */
    private static double met(int actual, int minimum) {
        if (actual >= minimum) {
            return 1.0;
        }
        return (double) actual / minimum;
    }

    /**
     * BR-7: one weighted average across every required skill, mandatory and preferred together.
     *
     * <p>A requirement listing no skills scores 1.0 rather than dividing by a zero weight sum. It
     * asked for nothing, so everybody satisfies it completely - and the alternative, a zero, would
     * rank every candidate for an open requirement as a bad fit for a bar that does not exist.
     */
    private static double weightedAverage(List<SkillContribution> contributions) {
        int totalWeight = contributions.stream().mapToInt(SkillContribution::weight).sum();
        if (totalWeight == 0) {
            return 1.0;
        }

        double weighted = contributions.stream()
                .mapToDouble(skill -> skill.weight() * skill.contribution())
                .sum();

        return weighted / totalWeight;
    }

    /**
     * Three decimals, half-up.
     *
     * <p>Applied before the candidates are sorted, not after. Sorting on full precision and then
     * rounding for display produces an order the response cannot account for - two candidates both
     * reading 0.750, one above the other, with nothing on screen to say why.
     */
    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
