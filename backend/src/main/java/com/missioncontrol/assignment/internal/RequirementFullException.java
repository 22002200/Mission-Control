package com.missioncontrol.assignment.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Every place on this requirement is taken - invariant A2.
 *
 * <p>Raised on an offer and again on an acceptance, and the second case is the interesting one: a
 * seat can fill between an offer being made and its being answered, so an offer that was valid when
 * it was sent is not a promise that it can still be taken up.
 *
 * <p>Carries the counts rather than only saying no. A lead looking at a full line needs to know
 * whether it is full of acceptances - in which case the line is done - or of outstanding offers,
 * in which case withdrawing one frees it. Those are different actions and a bare 409 chooses
 * neither.
 */
class RequirementFullException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:requirement-full");

    private final UUID requirementId;
    private final int requiredCount;
    private final int acceptedCount;
    private final int offeredCount;

    RequirementFullException(UUID requirementId, int requiredCount, int acceptedCount,
                             int offeredCount) {
        super(HttpStatus.CONFLICT, TYPE, "Requirement full", detailFor(requiredCount, offeredCount));
        this.requirementId = requirementId;
        this.requiredCount = requiredCount;
        this.acceptedCount = acceptedCount;
        this.offeredCount = offeredCount;
    }

    /**
     * Two sentences, because the way out differs.
     *
     * <p>A line full of acceptances is finished. A line full of outstanding offers is waiting, and
     * saying so points at the withdrawal that would free a place instead of leaving a lead to work
     * that out from a number.
     */
    private static String detailFor(int requiredCount, int offeredCount) {
        String seats = requiredCount == 1 ? "Its one place is" : "All " + requiredCount + " places are";
        return offeredCount > 0
                ? seats + " taken, some by offers nobody has answered yet."
                : seats + " already filled.";
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("requirementId", requirementId.toString());
        problem.setProperty("requiredCount", requiredCount);
        problem.setProperty("acceptedCount", acceptedCount);
        problem.setProperty("offeredCount", offeredCount);
        return problem;
    }
}
