package com.missioncontrol.shared;

/**
 * What a user is allowed to do. Exactly one per user - invariant I2.
 *
 * <p>Lives in the shared kernel rather than in {@code identity}, which owns the {@code User} it
 * describes, for one structural reason: {@code platform} has to express the role of the caller on
 * an authenticated request, and {@code platform} may not depend on {@code identity} - that is the
 * cycle {@code ModularityTests} exists to prevent. It also clears the bar the shared kernel sets,
 * since {@code skill}, {@code mission} and {@code crew} all need to name a role too.
 *
 * <p>The codes are <strong>pinned and append-only</strong>, per {@code docs/data-model.md}. The
 * integer is what is stored, so renumbering a constant silently re-points every existing row at a
 * different role. Add new roles at the end; never reorder and never reuse.
 *
 * <p>The code is a persistence detail. On the wire a role always travels as its name
 * ({@code 'DIRECTOR'}), never as its integer.
 */
public enum UserRole {

    DIRECTOR(1),
    MISSION_LEAD(2),
    CREW_MEMBER(3);

    private final int code;

    UserRole(int code) {
        this.code = code;
    }

    /** The pinned integer stored in the database. */
    public int code() {
        return code;
    }

    /**
     * Resolves a stored code back to a role.
     *
     * @throws IllegalArgumentException if the code is not one this version knows. Failing loudly
     *         beats returning null and letting an unmapped row flow through the application as an
     *         absent role.
     */
    public static UserRole fromCode(int code) {
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown UserRole code: " + code);
    }
}
