package com.missioncontrol.identity.internal;

import com.missioncontrol.platform.ApiProblemException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The email did not match a user, or the password was wrong.
 *
 * <p>One exception for both causes, with no constructor argument, so the two are indistinguishable
 * to the caller. Telling them apart would turn login into an account-enumeration oracle.
 */
class InvalidCredentialsException extends ApiProblemException {

    private static final URI TYPE = URI.create("urn:mission-control:invalid-credentials");

    InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, TYPE, "Invalid credentials",
                "Email or password is incorrect.");
    }
}
