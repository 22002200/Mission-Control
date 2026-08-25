package com.missioncontrol.assignment.internal;

import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.ProblemTypes;
import org.springframework.http.HttpStatus;

/**
 * No assignment, mission or crew member with that id that the caller may act on.
 *
 * <p>One exception for several causes - it does not exist, it belongs to another organisation, or
 * the crew member being offered a place is somebody else's employee - and no id in the detail.
 * Telling them apart would let a caller probe another tenant for which ids are real, which is the
 * leak invariant T2 exists to prevent. The same reasoning as {@code MissionNotFoundException},
 * which this deliberately reads identically to.
 */
class AssignmentNotFoundException extends ApiProblemException {

    AssignmentNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Not found", detail);
    }

    static AssignmentNotFoundException assignment() {
        return new AssignmentNotFoundException("No such assignment.");
    }

    /**
     * Covers both an unknown requirement and one that exists on a different mission.
     *
     * <p>Deliberately the same answer for both. Distinguishing them would let a caller confirm a
     * requirement id is real by pairing it with a mission they can see - the same leak
     * {@code MissionPlan.requirement} documents.
     */
    static AssignmentNotFoundException requirement() {
        return new AssignmentNotFoundException("No such crew requirement on this mission.");
    }

    static AssignmentNotFoundException crewMember() {
        return new AssignmentNotFoundException("No such crew member.");
    }
}
