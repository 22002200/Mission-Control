package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * A mission cannot start until every requirement is filled - invariant M11.
 *
 * <p>The response lists which requirements are short and by how much, rather than saying only that
 * something is missing. A mission lead looking at eight requirements needs to know which two to
 * chase, and making them compare the detail view by eye is a worse answer than three extra fields.
 *
 * <p>Note this is a precondition of starting, not a standing invariant: crew withdrawing from an
 * already-running mission does not send it backwards.
 */
class MissionUnderstaffedException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:mission-understaffed");

    private final List<Shortfall> shortfalls;

    MissionUnderstaffedException(List<Shortfall> shortfalls) {
        super(HttpStatus.CONFLICT, TYPE, "Mission understaffed", detailFor(shortfalls));
        this.shortfalls = List.copyOf(shortfalls);
    }

    /**
     * The empty case is a different sentence, not a count of zero.
     *
     * <p>A mission with no requirements at all reaches this too. Saying '0 crew requirements are
     * not yet filled' would read as though nothing were wrong, which is precisely the vacuous
     * reading M12 exists to close.
     */
    private static String detailFor(List<Shortfall> shortfalls) {
        return switch (shortfalls.size()) {
            case 0 -> "This mission has no crew requirements, so there is nobody to fly it.";
            case 1 -> "One crew requirement is not yet filled.";
            default -> shortfalls.size() + " crew requirements are not yet filled.";
        };
    }

    @Override
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = super.toProblemDetail();
        problem.setProperty("requirements", shortfalls.stream().map(Shortfall::asProperty).toList());
        return problem;
    }

    /** One unfilled requirement, as the error reports it. */
    record Shortfall(UUID id, String title, int requiredCount, int acceptedCount) {

        private Map<String, Object> asProperty() {
            return Map.of(
                    "id", id.toString(),
                    "title", title,
                    "requiredCount", requiredCount,
                    "acceptedCount", acceptedCount);
        }
    }
}
