package com.missioncontrol.matching.internal;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drafting one crew member per open seat across a whole mission - BR-10.
 *
 * <p>The problem this solves is that requirements are ranked independently, so the same person can
 * top two lists. Handing a mission lead a draft that puts one crew member in two seats is not a
 * suggestion, it is a contradiction the server would refuse anyway - invariant A5 allows one
 * non-terminal assignment per person per mission.
 *
 * <p><strong>Most-constrained-first.</strong> Requirements are served in ascending order of how
 * many people are eligible for them at all. A line with two possible candidates gets first refusal
 * over one with forty, because the forty-strong line loses almost nothing by dropping to its
 * second choice and the two-strong line may have nobody left. The tie-break is the requirement id,
 * so the order is stable across identical requests - NFR-1.
 *
 * <p><strong>Greedy, not optimal.</strong> A globally optimal assignment exists and is not what
 * this is. Greedy is one pass, it can be explained to a mission lead in a sentence, and the lead
 * overrides it seat by seat the moment they disagree - at which point a guarantee of global
 * optimality would be broken by the first thing they did with it.
 *
 * <p>Pure and static: no Spring, no clock, no data access. All of BR-10 is testable by handing it
 * lists.
 */
final class MatchAllocator {

    private MatchAllocator() {
    }

    /**
     * Assigns candidates to open seats, nobody twice.
     *
     * @param requirements every requirement on the mission with its ranked candidates, in any order
     * @return candidates per requirement id, best first, at most {@code openSeats} each. A
     *         requirement with no open seats or nobody left maps to an empty list rather than being
     *         absent, so a caller can render the whole mission from one result without having to
     *         distinguish nothing-to-do from nothing-reported.
     */
    static Map<UUID, List<ScoredCandidate>> allocate(List<RankedRequirement> requirements) {
        Set<UUID> claimed = new HashSet<>();
        Map<UUID, List<ScoredCandidate>> drafted = new HashMap<>();

        requirements.stream()
                .sorted(Comparator.comparingInt(RankedRequirement::eligibleCount)
                        .thenComparing(entry -> entry.requirement().id()))
                .forEach(entry -> {
                    List<ScoredCandidate> take = entry.ranked().stream()
                            .filter(candidate -> !claimed.contains(candidate.crewMemberId()))
                            .limit(entry.requirement().openSeats())
                            .toList();

                    take.forEach(candidate -> claimed.add(candidate.crewMemberId()));
                    drafted.put(entry.requirement().id(), take);
                });

        return drafted;
    }
}
