package com.missioncontrol.platform;

import com.missioncontrol.shared.UserRole;
import java.util.Optional;
import java.util.UUID;

/**
 * The caller of the current request.
 *
 * <p>This is how every module obtains the organisation it must scope its queries to. It is an
 * injected bean rather than a static holder so services stay unit-testable, and rather than a
 * resolved controller argument because a custom argument resolver leaks into the OpenAPI document
 * as a query parameter unless every single occurrence remembers to hide it - and the generated
 * TypeScript client is committed, so one oversight ships.
 *
 * <p>No endpoint anywhere accepts an organisation id from a path, query or body. The token is the
 * only source - that is requirement FR-8, and it is what makes invariant T1 enforceable.
 */
public interface CurrentUser {

    /**
     * @throws IllegalStateException if the request is not authenticated. Callers reached through
     *         an authenticated endpoint can treat this as impossible; it means the filter chain
     *         let something through that it should not have.
     */
    AuthenticatedUser require();

    /** Empty when the request is anonymous. */
    Optional<AuthenticatedUser> find();

    default UUID userId() {
        return require().userId();
    }

    /** The tenant every query on this request must be filtered by. */
    default UUID organisationId() {
        return require().organisationId();
    }

    default UserRole role() {
        return require().role();
    }
}
