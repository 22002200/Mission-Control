/**
 * Organisations and the accounts that belong to them: logging in, logging out, and who the caller
 * is.
 *
 * <p>The first <strong>closed</strong> module in the application, and now the second to publish
 * anything. {@link com.missioncontrol.identity.api.UserDirectory} was added for feature 04:
 * {@code mission} stores its owning lead as a bare id, because a foreign key may not cross a
 * module boundary, yet every mission has to be displayed with a person's name on it. The
 * {@code api} package is two types and should stay close to that size - everything else, including
 * the entities, the repository and the controller, remains in {@code internal}.
 *
 * <p>Note what is <em>not</em> published. There is no way to ask this module for a user's role,
 * because the one rule that turns on one - directors do not own missions, invariant M2 - is
 * already settled where a mission is created: the owner is the caller, and the endpoint is behind
 * a role check. A published role getter would only invite a weaker second check somewhere else.
 *
 * <p>{@code UserRole} is the other exception, and it lives in {@code shared} rather than here. See
 * {@link com.missioncontrol.shared.UserRole} for why.
 *
 * <p>The allow-list is stated explicitly even though both entries are OPEN modules that need no
 * declaration. It costs one line and it fails the build the day someone reaches into a domain
 * module from here.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"platform", "shared"}
)
package com.missioncontrol.identity;
