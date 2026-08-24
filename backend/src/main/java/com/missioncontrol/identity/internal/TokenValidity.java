package com.missioncontrol.identity.internal;

import java.time.Instant;

/**
 * The two facts the per-request token check needs, and nothing else.
 *
 * <p>A projection rather than the whole entity because this is read on every authenticated
 * request. It also keeps 'this user has never logged out' ({@code tokensValidFrom} null)
 * distinguishable from 'there is no such user' (no row at all), which matters: the first is fine
 * and the second must fail closed.
 *
 * @param tokensValidFrom null until the user's first logout
 * @param status          so a token stops working the moment its account is disabled
 */
record TokenValidity(Instant tokensValidFrom, UserStatus status) {
}
