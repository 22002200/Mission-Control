/**
 * What other modules may use from {@code skill}: resolving a skill id to a name and an active
 * flag.
 *
 * <p>Declared as a named interface so Spring Modulith actually exposes it. A closed module
 * publishes only its base package by default, so without this annotation the {@code api} directory
 * would be a convention with nothing enforcing it - and every import of it would fail the build
 * exactly as an import of {@code internal} does.
 */
@org.springframework.modulith.NamedInterface("api")
package com.missioncontrol.skill.api;
