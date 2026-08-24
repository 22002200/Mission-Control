/**
 * What other modules may use from {@code identity}: turning user ids into display names.
 *
 * <p>Declared as a named interface so Spring Modulith exposes it. A closed module publishes only
 * its base package by default, so without this annotation an import of {@code identity.api} would
 * fail the build exactly as an import of {@code identity.internal} does.
 *
 * <p>Nothing here can authenticate anybody, and nothing reveals a role or an email. Logging in
 * stays behind the module boundary.
 */
@org.springframework.modulith.NamedInterface("api")
package com.missioncontrol.identity.api;
