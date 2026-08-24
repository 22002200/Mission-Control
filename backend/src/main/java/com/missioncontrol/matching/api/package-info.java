/**
 * What crosses this module's boundary.
 *
 * <p>As with {@code mission.api}, the one type here is not something this module offers but
 * something it asks for. {@link com.missioncontrol.matching.api.CrewLoadReadModel} is a port that
 * {@code assignment} will implement in feature 07, which keeps the compile-time dependency
 * pointing from {@code assignment} to here rather than the other way and avoids the cycle
 * {@code ModularityTests} exists to catch.
 *
 * <p>Declared as a named interface so Spring Modulith actually exposes it - a closed module
 * publishes only its base package by default.
 */
@org.springframework.modulith.NamedInterface("api")
package com.missioncontrol.matching.api;
