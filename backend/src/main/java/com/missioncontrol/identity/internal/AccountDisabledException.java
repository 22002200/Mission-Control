package com.missioncontrol.identity.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The credentials were right, but the account is not {@code ACTIVE} - invariant I4.
 *
 * <p>Only ever raised after the password has been verified. Raising it earlier would let anyone
 * discover which accounts exist and are disabled without knowing a password.
 */
class AccountDisabledException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:account-disabled");

    AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, TYPE, "Account disabled",
                "This account has been disabled. Contact your organisation's director.");
    }
}
