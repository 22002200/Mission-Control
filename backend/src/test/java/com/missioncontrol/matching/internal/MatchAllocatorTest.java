package com.missioncontrol.matching.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.missioncontrol.mission.api.RequirementPlan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drafting a whole mission at once - BR-10.
 *
 * <p>Pure input, pure output. The rule that matters is that no crew member is drafted twice, and
 * the rule that decides who wins a contest is that the more constrained requirement gets first
 * refusal.
 */
class MatchAllocatorTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRUNO = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHEN = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static final UUID SCARCE = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID PLENTIFUL = UUID.fromString("a5000000-0000-0000-0000-000000000002");

    private static RequirementPlan requirement(UUID id, int requiredCount, int accepted,
                                               int offered) {
        return new RequirementPlan(id, "Line " + id, requiredCount, accepted, offered, List.of());
    }

    private static ScoredCandidate candidate(UUID crewMemberId, double score) {
        return new ScoredCandidate(crewMemberId, score, score, 0, 0, 0, 0, List.of());
    }

    @Test
    @DisplayName("Each requirement is filled up to its open seats, best first")
    void fillsOpenSeats() {
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(SCARCE, 2, 0, 0),
                        List.of(candidate(ADA, 1.0), candidate(BRUNO, 0.8),
                                candidate(CHEN, 0.6)))));

        assertThat(drafted.get(SCARCE))
                .extracting(ScoredCandidate::crewMemberId)
                .containsExactly(ADA, BRUNO);
    }

    @Test
    @DisplayName("Seats already accepted or offered are not drafted for again")
    void openSeatsAccountForExistingAssignments() {
        // Three seats, one accepted and one offered, so exactly one is still open.
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(SCARCE, 3, 1, 1),
                        List.of(candidate(ADA, 1.0), candidate(BRUNO, 0.8)))));

        assertThat(drafted.get(SCARCE)).extracting(ScoredCandidate::crewMemberId)
                .containsExactly(ADA);
    }

    @Test
    @DisplayName("A fully staffed requirement is present with an empty list, not absent")
    void fullyStaffedRequirementIsStillReported() {
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(SCARCE, 2, 2, 0),
                        List.of(candidate(ADA, 1.0)))));

        assertThat(drafted).containsKey(SCARCE);
        assertThat(drafted.get(SCARCE)).isEmpty();
    }

    @Test
    @DisplayName("Over-staffing cannot produce a negative number of seats to draft")
    void openSeatsAreFlooredAtZero() {
        // Invariant A2 should make this unreachable, but a negative limit would fail somewhere far
        // less obvious than here.
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(SCARCE, 1, 2, 1),
                        List.of(candidate(ADA, 1.0)))));

        assertThat(drafted.get(SCARCE)).isEmpty();
    }

    @Test
    @DisplayName("A crew member topping two requirements is drafted onto exactly one")
    void neverDraftsTheSamePersonTwice() {
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(PLENTIFUL, 1, 0, 0),
                        List.of(candidate(ADA, 1.0), candidate(BRUNO, 0.9),
                                candidate(CHEN, 0.8))),
                new RankedRequirement(requirement(SCARCE, 1, 0, 0),
                        List.of(candidate(ADA, 1.0)))));

        assertThat(drafted.values().stream().flatMap(List::stream)
                .map(ScoredCandidate::crewMemberId).toList())
                .doesNotHaveDuplicates();
    }

    /**
     * The whole point of most-constrained-first. Ada tops both lines, but only one of them has
     * anybody else, so giving her to the line with alternatives would leave the other empty.
     */
    @Test
    @DisplayName("A contested candidate goes to the requirement with fewer alternatives")
    void servesTheMostConstrainedRequirementFirst() {
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(PLENTIFUL, 1, 0, 0),
                        List.of(candidate(ADA, 1.0), candidate(BRUNO, 0.9),
                                candidate(CHEN, 0.8))),
                new RankedRequirement(requirement(SCARCE, 1, 0, 0),
                        List.of(candidate(ADA, 1.0)))));

        assertThat(drafted.get(SCARCE)).extracting(ScoredCandidate::crewMemberId)
                .containsExactly(ADA);
        assertThat(drafted.get(PLENTIFUL)).extracting(ScoredCandidate::crewMemberId)
                .containsExactly(BRUNO);
    }

    @Test
    @DisplayName("A requirement whose only candidate was taken comes back empty rather than absent")
    void requirementLeftWithNobodyIsStillReported() {
        Map<UUID, List<ScoredCandidate>> drafted = MatchAllocator.allocate(List.of(
                new RankedRequirement(requirement(SCARCE, 1, 0, 0), List.of(candidate(ADA, 1.0))),
                new RankedRequirement(requirement(PLENTIFUL, 1, 0, 0),
                        List.of(candidate(ADA, 1.0)))));

        // Both lines have one eligible candidate, so the requirement id breaks the tie and SCARCE
        // - the lower id - is served first.
        assertThat(drafted.get(SCARCE)).extracting(ScoredCandidate::crewMemberId)
                .containsExactly(ADA);
        assertThat(drafted).containsKey(PLENTIFUL);
        assertThat(drafted.get(PLENTIFUL)).isEmpty();
    }

    @Test
    @DisplayName("Two equally constrained requirements resolve the same way every time")
    void tieBreakIsStable() {
        List<RankedRequirement> input = List.of(
                new RankedRequirement(requirement(PLENTIFUL, 1, 0, 0), List.of(candidate(ADA, 1.0))),
                new RankedRequirement(requirement(SCARCE, 1, 0, 0), List.of(candidate(ADA, 1.0))));

        // Reversed input, identical outcome: the order requirements arrive in must not matter.
        assertThat(MatchAllocator.allocate(input))
                .isEqualTo(MatchAllocator.allocate(input.reversed()));
    }

    @Test
    @DisplayName("A mission with no requirements allocates nothing without complaining")
    void emptyMissionIsFine() {
        assertThat(MatchAllocator.allocate(List.of())).isEmpty();
    }
}
