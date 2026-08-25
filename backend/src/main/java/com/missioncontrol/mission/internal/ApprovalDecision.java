package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;

/**
 * The outcome of one submit-and-decide cycle on a mission.
 *
 * <p>Public, like {@link MissionStatus}, because it is a component of the public
 * {@code MissionApprovalResponse} record and a package-private type cannot be. It is still an
 * {@code internal} type: no other module may name it.
 *
 * <p>Codes are <strong>pinned and append-only</strong>, per {@code docs/data-model.md}. On the
 * wire a decision always travels as its name.
 *
 * <p>{@code CANCELLED} is not a decision anyone makes. It is what becomes of an open cycle when
 * the mission it belongs to is closed out from under it - a director aborting a mission that was
 * awaiting their own decision, for instance. Leaving such a cycle {@code PENDING} would be worse
 * than untidy: it reads on screen as still waiting for someone, and it would hold the partial
 * unique index behind invariant M8 forever. Because the codes are append-only, 4 is the only
 * place it could go.
 */
public enum ApprovalDecision {

    PENDING(1),
    APPROVED(2),
    REJECTED(3),
    CANCELLED(4);

    private final int code;

    ApprovalDecision(int code) {
        this.code = code;
    }

    /** The pinned integer stored in the database. */
    public int code() {
        return code;
    }

    /**
     * Resolves a stored code back to a decision.
     *
     * @throws IllegalArgumentException if the code is not one this version knows. Failing loudly
     *         beats letting an unmapped row flow through the application as a null decision.
     */
    public static ApprovalDecision fromCode(int code) {
        for (ApprovalDecision decision : values()) {
            if (decision.code == code) {
                return decision;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalDecision code: " + code);
    }
}
