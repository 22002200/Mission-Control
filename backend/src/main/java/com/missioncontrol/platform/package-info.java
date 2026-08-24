/**
 * Cross-cutting infrastructure: security, CORS, OpenAPI metadata, error handling and
 * system endpoints.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} because every
 * domain module is expected to depend on this plumbing. Domain modules must NOT be open - they
 * expose an {@code api} package and hide everything else in {@code internal}.
 *
 * <p>Keep this module free of domain concepts. If something here starts to know what a Mission or
 * a Crew Member is, it belongs in a domain module instead. Note that
 * {@link com.missioncontrol.platform.AuthenticatedUser} is not a counter-example: a user id, a
 * tenant id and a role are facts about <em>who is calling</em>, which is precisely what a security
 * layer is for. The line is that nothing here knows what those users do.
 *
 * <p>The dependency arrow points one way. {@code identity} depends on this module; this module must
 * never refer to {@code identity}. Where {@code platform} needs a decision only {@code identity}
 * can make - is this token still valid for its user? - it accepts a contributed
 * {@code OAuth2TokenValidator} rather than calling into the module. See
 * {@link com.missioncontrol.platform.JwtSecurityConfig}.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform"
)
package com.missioncontrol.platform;
