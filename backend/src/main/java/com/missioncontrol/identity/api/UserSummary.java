package com.missioncontrol.identity.api;

import java.util.UUID;

/**
 * Enough of a user to name them on someone else's screen.
 *
 * <p>Two fields, and the omissions are the point. No email, because a display name is what a
 * mission card needs and an address is contact data nothing has asked for. No role either: the one
 * rule that turns on a role - only mission leads own missions, invariant M2 - is already
 * guaranteed where a mission is created, because the owner is the caller and the endpoint is
 * behind a role check. Publishing a field to re-derive a fact that is structurally true would be
 * inviting a second, weaker check.
 *
 * @param id       the account
 * @param fullName as the person is displayed
 */
public record UserSummary(UUID id, String fullName) {
}
