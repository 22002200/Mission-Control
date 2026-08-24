package com.missioncontrol.mission.internal;

/**
 * Why a mission ended.
 *
 * <p>Abort is not a status. The product spec lists six states and closing is one of them, so an
 * aborted mission is a closed mission whose reason says so - invariant M4 ties the two together in
 * both directions, and the database enforces it.
 *
 * <p>Codes are pinned and append-only, like every other enum here.
 */
public enum MissionCloseReason {

    COMPLETED(1),
    ABORTED(2),
    REJECTED(3);

    private final int code;

    MissionCloseReason(int code) {
        this.code = code;
    }

    /** The pinned integer stored in the database. */
    public int code() {
        return code;
    }

    public static MissionCloseReason fromCode(int code) {
        for (MissionCloseReason reason : values()) {
            if (reason.code == code) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown MissionCloseReason code: " + code);
    }
}
