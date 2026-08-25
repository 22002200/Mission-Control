package com.missioncontrol.assignment.internal;

import java.util.Set;

/**
 * Where an offer of a place has got to.
 *
 * <p>Public for the same reason {@code MissionStatus} was before it moved: it is a component of
 * public response records, and a package-private type cannot be. It is still internal - no other
 * module may name it, and {@code ModularityTests} enforces that.
 *
 * <p>Codes are <strong>pinned and append-only</strong>, per {@code docs/data-model.md}. The integer
 * is what is stored, so renumbering a constant silently re-points existing rows at a different
 * state. On the wire a status always travels as its name.
 *
 * <p>There is deliberately no {@code COMPLETED}. Completion is a fact about the mission, not about
 * the offer, and a crew member's history is derived from acceptances on missions closed as
 * {@code COMPLETED}. A fourth terminal state here would be a second place for that to be recorded
 * and a second place for it to be wrong.
 *
 * <p>The permitted transitions live here rather than in the service - invariant A7 - so they can be
 * tested exhaustively without a Spring context or a database, exactly as {@code MissionStatus}
 * does for M3.
 */
public enum AssignmentStatus {

    OFFERED(1),
    ACCEPTED(2),
    DECLINED(3),
    WITHDRAWN(4);

    private final int code;

    AssignmentStatus(int code) {
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
    public static AssignmentStatus fromCode(int code) {
        for (AssignmentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AssignmentStatus code: " + code);
    }

    /**
     * Invariant A7, in full.
     *
     * <p>Stated as an allow-list rather than as guards scattered across the service, so adding a
     * state forces a decision about every arrow into and out of it.
     *
     * <p>Note what this does <em>not</em> say. It permits {@code ACCEPTED} to {@code WITHDRAWN} and
     * says nothing about who may ask for it; that is BR-9, and the answer is the owning mission
     * lead alone. A crew member who has accepted is assigned.
     */
    public boolean canTransitionTo(AssignmentStatus target) {
        return switch (this) {
            case OFFERED -> Set.of(ACCEPTED, DECLINED, WITHDRAWN).contains(target);
            case ACCEPTED -> target == WITHDRAWN;
            case DECLINED, WITHDRAWN -> false;
        };
    }

    /** Nothing may follow a declined or withdrawn offer - A7. */
    public boolean isTerminal() {
        return this == DECLINED || this == WITHDRAWN;
    }

    /**
     * Whether this assignment still counts against a requirement's capacity - invariant A2.
     *
     * <p>Offered and accepted both do. An offer reserves no crew member's calendar - A4 - but it
     * does reserve the seat, otherwise a lead could offer the same place to everyone and find out
     * later that four people accepted it.
     */
    public boolean occupiesSeat() {
        return this == OFFERED || this == ACCEPTED;
    }
}
