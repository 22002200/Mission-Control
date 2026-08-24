package com.missioncontrol.platform;

/**
 * Mints access tokens.
 *
 * <p>Published by {@code platform} so that the signing secret, the claim names and the algorithm
 * live in exactly one place, next to the decoder that verifies them. A module that needs to log
 * someone in supplies the three facts in {@link AuthenticatedUser} and gets a token back; it never
 * sees the secret and never names a claim.
 */
public interface TokenIssuer {

    IssuedToken issue(AuthenticatedUser user);
}
