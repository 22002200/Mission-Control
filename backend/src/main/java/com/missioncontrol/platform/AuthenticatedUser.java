package com.missioncontrol.platform;

import com.missioncontrol.shared.UserRole;
import java.util.UUID;

/**
 * Who is making the current request, as established by their token.
 *
 * <p>These three facts are the whole basis of authorisation: identity, tenant and permission. They
 * are authentication concepts rather than domain concepts, which is why they live in
 * {@code platform} - nothing here knows what a Mission is.
 *
 * @param userId         the authenticated user
 * @param organisationId the tenant every query the request makes must be scoped to
 * @param role           the caller's single role
 */
public record AuthenticatedUser(UUID userId, UUID organisationId, UserRole role) {
}
