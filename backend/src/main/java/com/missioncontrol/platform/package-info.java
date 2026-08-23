/**
 * Cross-cutting infrastructure: security, CORS, OpenAPI metadata, error handling and
 * system endpoints.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} because every
 * domain module is expected to depend on this plumbing. Domain modules must NOT be open - they
 * expose an {@code api} package and hide everything else in {@code internal}.
 *
 * <p>Keep this module free of domain concepts. If something here starts to know what a Mission or
 * a Crew Member is, it belongs in a domain module instead.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform"
)
package com.missioncontrol.platform;
