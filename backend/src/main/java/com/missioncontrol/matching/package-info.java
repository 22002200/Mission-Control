/**
 * The crew matching engine: given a crew requirement, who could fill it and in what order.
 *
 * <p>Owns no data. Every fact it works from belongs to another module - the mission window and its
 * requirements to {@code mission}, the roster and its ratings to {@code crew}, skill names to
 * {@code skill}, display names to {@code identity}, and assignment history to {@code assignment} -
 * and it reads all of them through published interfaces. What it adds is the arithmetic, and the
 * arithmetic is the feature.
 *
 * <p>Read-only in the strongest sense: no entity, no repository, no migration, nothing in this
 * module can write. It suggests; acting on a suggestion is feature 07.
 *
 * <p>The dependency worth explaining is the one that is missing. {@code assignment} does not exist
 * yet, and when it does the arrow must point from it to {@code mission}, not back through here. So
 * the assignment data this module needs is declared as
 * {@link com.missioncontrol.matching.api.CrewLoadReadModel} - a port on the consumer's side, the
 * same shape {@code mission} already uses for {@code StaffingReadModel} - with a no-op standing in
 * until 07 supplies the real one. Matching still returns correct rankings meanwhile: with no
 * assignments there is nobody to exclude and no load to penalise.
 *
 * <p>{@code shared} is deliberately absent from the allow-list. Who may run a match is
 * owner-or-director, which needs the mission in hand, so {@code mission} answers it through
 * {@code MissionPlans} and nothing here ever names a {@code UserRole}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Matching",
        allowedDependencies = {
                "platform",
                "mission :: api",
                "crew :: api",
                "skill :: api",
                "identity :: api"
        }
)
package com.missioncontrol.matching;
