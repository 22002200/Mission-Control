package com.missioncontrol.matching.internal;

import com.missioncontrol.mission.api.RequirementPlan;
import java.util.List;

/**
 * One requirement together with everyone eligible for it, already in ranked order.
 *
 * <p>The unit the allocator works in. Keeping the requirement and its candidates together means
 * the allocator never has to look anything up by id while it is deciding, which is what keeps
 * BR-10 a single pass.
 *
 * @param requirement the staffing line, including how many seats are still open
 * @param ranked      every candidate who passed the hard filters, best first - BR-9's order
 */
record RankedRequirement(RequirementPlan requirement, List<ScoredCandidate> ranked) {

    /**
     * How constrained this line is: the number of people who could fill it at all.
     *
     * <p>Not the number of open seats. A requirement needing four people from a pool of forty is
     * far less constrained than one needing one person from a pool of two, and it is the pool that
     * decides who has alternatives if their first choice is taken.
     */
    int eligibleCount() {
        return ranked.size();
    }
}
