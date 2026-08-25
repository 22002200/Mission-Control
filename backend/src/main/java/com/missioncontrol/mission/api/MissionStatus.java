package com.missioncontrol.mission.api;

import java.util.Set;

/**
 * Where a mission is in its lifecycle.
 *
 * <p>Published, unlike {@code UserStatus} in {@code identity}, and it did not start that way. It
 * moved out of {@code internal} for feature 07: {@code GET /api/assignments/me} renders the status
 * of the mission each assignment is on, so {@code assignment} has to name this type. Declaring a
 * parallel enum over there instead would put a second copy of pinned, append-only codes in the
 * codebase, which is precisely the drift {@code docs/data-model.md} warns about.
 *
 * <p>{@code MissionCloseReason} deliberately did <em>not</em> come with it. No other module needs
 * to distinguish aborted from completed by name - {@code assignment} only asks whether a mission
 * completed, which {@link MissionWindow} answers as a boolean.
 *
 * <p>Codes are <strong>pinned and append-only</strong>, per {@code docs/data-model.md}. The integer
 * is what is stored, so renumbering a constant silently re-points existing missions at a different
 * state. On the wire a status always travels as its name.
 *
 * <p>The permitted transitions live here rather than in the service. They are a property of the
 * lifecycle itself, they are cited by number in three specs, and keeping them on the enum means
 * they can be tested exhaustively without a Spring context or a database.
 */
public enum MissionStatus {

    PLAN(1),
    PENDING_APPROVAL(2),
    APPROVED(3),
    REJECTED(4),
    ACTIVE(5),
    CLOSED(6);

    private final int code;

    MissionStatus(int code) {
        this.code = code;
    }

    /** The pinned integer stored in the database. */
    public int code() {
        return code;
    }

    /**
     * Resolves a stored code back to a status.
     *
     * @throws IllegalArgumentException if the code is not one this version knows. Failing loudly
     *         beats letting an unmapped row flow through the application as a null state.
     */
    public static MissionStatus fromCode(int code) {
        for (MissionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown MissionStatus code: " + code);
    }

    /** Nothing may follow {@code CLOSED} - invariant M3. */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /**
     * Invariant M3, in full.
     *
     * <p>Stated as an allow-list rather than as a set of guards scattered across the service, so
     * that adding a state forces a decision about every arrow into and out of it. Note that
     * {@code APPROVED} and {@code ACTIVE} may both drop back to {@code PLAN}: that is M5, an edit
     * discarding an approval, not a transition anyone requests directly.
     */
    public boolean canTransitionTo(MissionStatus target) {
        return switch (this) {
            case PLAN -> Set.of(PENDING_APPROVAL, CLOSED).contains(target);
            case PENDING_APPROVAL -> Set.of(APPROVED, REJECTED, CLOSED).contains(target);
            case REJECTED -> Set.of(PLAN, CLOSED).contains(target);
            case APPROVED -> Set.of(ACTIVE, PLAN, CLOSED).contains(target);
            case ACTIVE -> Set.of(PLAN, CLOSED).contains(target);
            case CLOSED -> false;
        };
    }
}
