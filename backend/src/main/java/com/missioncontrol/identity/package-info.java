/**
 * Organisations and the accounts that belong to them: logging in, logging out, and who the caller
 * is.
 *
 * <p>The first <strong>closed</strong> module in the application. Everything lives in
 * {@code internal} and nothing is published yet, because nothing outside this module needs a type
 * from it. When feature 04 has to check that a mission lead really is a {@code MISSION_LEAD} in the
 * right organisation, that is the moment to add an {@code api} package with a lookup interface -
 * not before. An {@code api} package with speculative types in it is not a boundary.
 *
 * <p>{@code UserRole} is the one exception, and it lives in {@code shared} rather than here. See
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
