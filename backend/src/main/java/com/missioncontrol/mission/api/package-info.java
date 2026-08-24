/**
 * What other modules may use from {@code mission}.
 *
 * <p>Unusually, the one type here is not something this module offers but something it asks for.
 * {@link com.missioncontrol.mission.api.StaffingReadModel} is a port: {@code assignment} will
 * implement it in feature 07, which keeps the compile-time dependency pointing from
 * {@code assignment} to {@code mission} and not the other way. Declaring it from the consumer side
 * is what makes that possible at all - the reverse is the cycle {@code ModularityTests} exists to
 * catch.
 */
@org.springframework.modulith.NamedInterface("api")
package com.missioncontrol.mission.api;
