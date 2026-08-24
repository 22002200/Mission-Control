package com.missioncontrol.mission.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * How long this organisation's missions actually run for.
 *
 * <p>Published for feature 06's load penalty. A fixed recency window means two different things in
 * two organisations - one running three-day sorties cycles a crew member through dozens of
 * assignments a year, one running six-month expeditions manages two - so the window is scaled to
 * the organisation's own tempo instead. Mission dates belong to this module, so the figure is
 * derived here.
 *
 * <p>A second interface rather than another method on {@link MissionPlans}. The two answer
 * unrelated questions - one is about permission to see a mission, the other is an organisation-wide
 * statistic with no mission in sight - and architecture.md's rule is that an {@code api} package
 * stays small and specific rather than becoming a place where a module's exports collect.
 */
public interface MissionTempo {

    /**
     * The median duration of the organisation's completed missions.
     *
     * <p>Median rather than mean, and that choice is load-bearing: a mean is dragged a long way by
     * one freak three-year mission, and a median is not. It is what lets the caller use the figure
     * without a floor or a ceiling to guard against outliers.
     *
     * <p>Only missions closed as {@code COMPLETED} count. An aborted mission says nothing about how
     * long the work takes, and a rejected one never ran at all.
     *
     * @return empty when the organisation has completed no missions, which is a real state for a
     *         new tenant and not an error. The caller supplies its own default; answering with an
     *         arbitrary duration here would hide the difference between no history and a genuinely
     *         short one.
     */
    Optional<Duration> medianCompletedMissionDuration(UUID organisationId);
}
