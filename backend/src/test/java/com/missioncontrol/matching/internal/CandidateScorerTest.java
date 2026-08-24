package com.missioncontrol.matching.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.missioncontrol.crew.api.CrewProfile;
import com.missioncontrol.mission.api.RequiredSkillSpec;
import com.missioncontrol.mission.api.RequirementPlan;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The scoring rules, BR-2 and BR-5 through BR-9.
 *
 * <p>No Spring and no database: the scorer is pure, which is the whole reason it is a separate type
 * from the engine that feeds it. Every rule in the spec that is arithmetic is provable here.
 */
class CandidateScorerTest {

    private static final UUID EVA = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID ROBOTICS = UUID.fromString("a2000000-0000-0000-0000-000000000002");

    private static final UUID REQUIREMENT = UUID.fromString("a5000000-0000-0000-0000-000000000001");

    private final CandidateScorer scorer = new CandidateScorer(properties());

    private static MatchingProperties properties() {
        return new MatchingProperties(0.1, 0.3, 0.05, 0.3, 6, Duration.ofDays(365), 3, 10, 50);
    }

    private static RequirementPlan requiring(RequiredSkillSpec... skills) {
        return new RequirementPlan(REQUIREMENT, "Flight Engineer", 2, 0, 0, List.of(skills));
    }

    private static CrewProfile ratedAt(Map<UUID, Integer> ratings) {
        return new CrewProfile(UUID.randomUUID(), UUID.randomUUID(), ratings);
    }

    private Optional<ScoredCandidate> score(RequirementPlan requirement, CrewProfile candidate) {
        return scorer.score(requirement, candidate, 0, 0);
    }

    @Nested
    @DisplayName("Hard filter on mandatory skills - BR-2")
    class MandatoryFilter {

        @Test
        @DisplayName("Below a mandatory minimum is excluded entirely, not ranked last")
        void belowMandatoryMinimumIsExcluded() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 2)))).isEmpty();
        }

        @Test
        @DisplayName("A mandatory skill the candidate does not hold at all is excluded")
        void missingMandatorySkillIsExcluded() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(ROBOTICS, 5)))).isEmpty();
        }

        @Test
        @DisplayName("Exactly at the minimum clears the filter")
        void exactlyAtTheMinimumIsEligible() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 3)))).isPresent();
        }

        @Test
        @DisplayName("A preferred skill below its minimum is not a filter, only a lower score")
        void preferredSkillDoesNotExclude() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            assertThat(score(requirement, ratedAt(Map.of(ROBOTICS, 1))))
                    .get()
                    .extracting(ScoredCandidate::skillScore)
                    .isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("Mandatory skills prefer the least over-qualified - BR-5")
    class MandatoryFit {

        @Test
        @DisplayName("Exactly at the minimum scores 1.0")
        void exactFitScoresOne() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 3))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(1.0);
        }

        @Test
        @DisplayName("A perfect 5 against a minimum of 3 scores 0.0 - over-qualification costs")
        void fullyOverQualifiedScoresZero() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 5))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(0.0);
        }

        @Test
        @DisplayName("One above a minimum of 3 scores 0.5 - halfway up the headroom")
        void partiallyOverQualifiedScoresProRata() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 4))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(0.5);
        }

        /**
         * The formula's denominator is {@code 5 - minimum}, so a minimum of 5 divides by zero. The
         * answer is 1.0 and it is not a fudge: when the job needs the top of the scale, nobody can
         * be over-qualified for it.
         */
        @Test
        @DisplayName("A mandatory minimum of 5 scores 1.0 rather than dividing by zero")
        void minimumOfFiveScoresOne() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 5, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 5))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Preferred skills prefer the most capable - BR-6")
    class PreferredCredit {

        @Test
        @DisplayName("At or above the minimum scores 1.0, and exceeding it is not penalised")
        void meetingOrExceedingScoresOne() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            assertThat(score(requirement, ratedAt(Map.of(ROBOTICS, 4))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(1.0);
            assertThat(score(requirement, ratedAt(Map.of(ROBOTICS, 5))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Below the minimum earns partial credit, pro rata")
        void belowTheMinimumEarnsPartialCredit() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            assertThat(score(requirement, ratedAt(Map.of(ROBOTICS, 2))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(0.5);
        }

        @Test
        @DisplayName("A skill not held at all earns nothing")
        void absentSkillEarnsNothing() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 5))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(0.0);
        }

        @Test
        @DisplayName("A preferred skill held below its minimum is reported as a shortfall")
        void shortfallIsFlagged() {
            RequirementPlan requirement = requiring(
                    new RequiredSkillSpec(EVA, 3, true, 1),
                    new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            List<SkillContribution> skills =
                    score(requirement, ratedAt(Map.of(EVA, 3, ROBOTICS, 2))).orElseThrow().skills();

            assertThat(skills).filteredOn(SkillContribution::isShortfall)
                    .extracting(SkillContribution::skillId)
                    .containsExactly(ROBOTICS);
        }

        @Test
        @DisplayName("A mandatory skill is never a shortfall - falling short of one is exclusion")
        void mandatorySkillIsNeverAShortfall() {
            RequirementPlan requirement = requiring(new RequiredSkillSpec(EVA, 3, true, 1));

            assertThat(score(requirement, ratedAt(Map.of(EVA, 3))).orElseThrow().skills())
                    .noneMatch(SkillContribution::isShortfall);
        }
    }

    @Nested
    @DisplayName("Combining the two - BR-7")
    class WeightedAverage {

        /**
         * The worked example from the spec, and the ordering the seeded Orbital Dynamics roster was
         * built to reproduce. If this breaks, so does the demo.
         */
        @Test
        @DisplayName("EVA mandatory min 3 plus Robotics preferred min 4 ranks A, C, B")
        void workedExample() {
            RequirementPlan requirement = requiring(
                    new RequiredSkillSpec(EVA, 3, true, 1),
                    new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            double ada = score(requirement, ratedAt(Map.of(EVA, 3, ROBOTICS, 4)))
                    .orElseThrow().skillScore();
            double chen = score(requirement, ratedAt(Map.of(EVA, 3, ROBOTICS, 2)))
                    .orElseThrow().skillScore();
            double bruno = score(requirement, ratedAt(Map.of(EVA, 5, ROBOTICS, 4)))
                    .orElseThrow().skillScore();

            assertThat(ada).isEqualTo(1.00);
            assertThat(chen).isEqualTo(0.75);
            assertThat(bruno).isEqualTo(0.50);
            assertThat(score(requirement, ratedAt(Map.of(EVA, 2, ROBOTICS, 5)))).isEmpty();
        }

        @Test
        @DisplayName("Weight shifts the average towards the heavier skill")
        void weightsAreApplied() {
            // The mandatory skill scores 0 and the preferred one scores 1. At equal weight that is
            // 0.5; at three to one in favour of the mandatory skill it is 0.25.
            RequirementPlan even = requiring(
                    new RequiredSkillSpec(EVA, 3, true, 1),
                    new RequiredSkillSpec(ROBOTICS, 4, false, 1));
            RequirementPlan weighted = requiring(
                    new RequiredSkillSpec(EVA, 3, true, 3),
                    new RequiredSkillSpec(ROBOTICS, 4, false, 1));

            CrewProfile candidate = ratedAt(Map.of(EVA, 5, ROBOTICS, 4));

            assertThat(score(even, candidate).orElseThrow().skillScore()).isEqualTo(0.5);
            assertThat(score(weighted, candidate).orElseThrow().skillScore()).isEqualTo(0.25);
        }

        @Test
        @DisplayName("A requirement listing no skills scores 1.0 for everyone")
        void noSkillsScoresOne() {
            assertThat(score(requiring(), ratedAt(Map.of())))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Experience and load - BR-8")
    class ExperienceAndLoad {

        private final RequirementPlan anyone = requiring();

        @Test
        @DisplayName("Each completed mission adds 0.1")
        void experienceAddsPerMission() {
            assertThat(scorer.score(anyone, ratedAt(Map.of()), 2, 0).orElseThrow().score())
                    .isEqualTo(1.2);
        }

        @Test
        @DisplayName("The experience bonus is capped at 0.3 however long the career")
        void experienceIsCapped() {
            assertThat(scorer.score(anyone, ratedAt(Map.of()), 40, 0).orElseThrow())
                    .extracting(ScoredCandidate::experienceBonus).isEqualTo(0.3);
        }

        @Test
        @DisplayName("Each recent assignment subtracts 0.05")
        void loadSubtractsPerAssignment() {
            assertThat(scorer.score(anyone, ratedAt(Map.of()), 0, 2).orElseThrow().score())
                    .isEqualTo(0.9);
        }

        @Test
        @DisplayName("The load penalty is capped at 0.3 however overworked")
        void loadIsCapped() {
            assertThat(scorer.score(anyone, ratedAt(Map.of()), 0, 40).orElseThrow())
                    .extracting(ScoredCandidate::loadPenalty).isEqualTo(0.3);
        }

        @Test
        @DisplayName("Both caps together keep the two terms secondary to skill fit")
        void bothCapsApplyTogether() {
            ScoredCandidate scored =
                    scorer.score(anyone, ratedAt(Map.of()), 40, 40).orElseThrow();

            assertThat(scored.score()).isEqualTo(1.0);
            assertThat(scored.experienceBonus()).isEqualTo(0.3);
            assertThat(scored.loadPenalty()).isEqualTo(0.3);
        }
    }

    @Nested
    @DisplayName("Ordering - BR-9")
    class Ordering {

        @Test
        @DisplayName("Higher scores first, ties broken by crew member id")
        void ranksByScoreThenId() {
            UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

            ScoredCandidate lowScoreLowId = candidate(first, 0.5);
            ScoredCandidate tiedLowId = candidate(first, 0.9);
            ScoredCandidate tiedHighId = candidate(second, 0.9);

            assertThat(List.of(lowScoreLowId, tiedHighId, tiedLowId).stream()
                    .sorted(ScoredCandidate.ranking())
                    .map(ScoredCandidate::crewMemberId)
                    .toList())
                    .containsExactly(first, second, first);
        }

        private ScoredCandidate candidate(UUID id, double score) {
            return new ScoredCandidate(id, score, score, 0, 0, 0, 0, List.of());
        }
    }

    @Nested
    @DisplayName("Rounding")
    class Rounding {

        /**
         * Rounding happens before sorting, so the order can always be justified by the numbers on
         * screen. A third of a point is the classic case: 1/3 scores 0.333 and nothing downstream
         * ever sees the trailing digits that would otherwise decide a tie invisibly.
         */
        @Test
        @DisplayName("Scores are rounded to three decimals")
        void roundsToThreeDecimals() {
            RequirementPlan requirement = requiring(
                    new RequiredSkillSpec(EVA, 3, false, 1),
                    new RequiredSkillSpec(ROBOTICS, 3, false, 2));

            // EVA 1/3 met, Robotics absent: (1 x 0.333... + 2 x 0) / 3 = 0.111...
            assertThat(score(requirement, ratedAt(Map.of(EVA, 1))))
                    .get().extracting(ScoredCandidate::skillScore).isEqualTo(0.111);
        }
    }
}
