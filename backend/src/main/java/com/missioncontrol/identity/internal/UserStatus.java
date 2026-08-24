package com.missioncontrol.identity.internal;

/**
 * Whether an account may be used.
 *
 * <p>Internal to this module: only login cares, and nothing outside identity has any business
 * knowing an account is disabled. Codes are pinned and append-only, per {@code docs/data-model.md}.
 */
enum UserStatus {

    ACTIVE(1),
    DISABLED(2);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    int code() {
        return code;
    }

    static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus code: " + code);
    }
}
