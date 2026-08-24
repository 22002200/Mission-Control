package com.missioncontrol.matching.internal;

import com.missioncontrol.mission.api.MissionTempo;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * How far back an assignment still counts as recent, for this organisation.
 *
 * <p>A fixed year would mean two different things in two organisations. One running three-day
 * sorties cycles a crew member through dozens of assignments in twelve months; one running
 * six-month expeditions manages two. The same {@code recentAssignments} count therefore describes
 * an overworked crew member in the second and an unremarkable one in the first, and a fixed window
 * cannot tell them apart. Scaling to the organisation's own median mission length can.
 *
 * <p>Median, not mean, which is why there is no floor and no ceiling here. A mean is dragged a long
 * way by a single freak three-year mission and would need clamping; a median is not, so the clamp
 * would have nothing left to do.
 *
 * <p>One consequence is accepted rather than designed around: with one or two completed missions
 * the median is simply that mission's duration, so the window is volatile early in an
 * organisation's life. Only its own history can inform this, it converges quickly, and the penalty
 * is capped either way - so the cost of a bad early window is a fraction of a point on a term that
 * is already secondary to skill fit.
 */
@Component
class LoadWindow {

    private final MissionTempo tempo;
    private final MatchingProperties properties;

    LoadWindow(MissionTempo tempo, MatchingProperties properties) {
        this.tempo = tempo;
        this.properties = properties;
    }

    /**
     * The earliest mission start that still counts towards a crew member's load.
     *
     * <p>One cutoff covers both halves of BR-8 - within the recent window, or in the future -
     * because any mission starting ahead of now necessarily starts ahead of a cutoff behind it.
     * There is no separate future clause because there does not need to be one.
     */
    Instant recencyCutoff(UUID organisationId, Instant now) {
        return now.minus(durationFor(organisationId));
    }

    Duration durationFor(UUID organisationId) {
        return from(tempo.medianCompletedMissionDuration(organisationId),
                properties.loadWindowMultiplier(),
                properties.defaultLoadWindow());
    }

    /**
     * The window itself, separated from where the median comes from so the arithmetic can be tested
     * without a database.
     *
     * @param median     empty for an organisation that has completed no missions - a real state for
     *                   a new tenant, and the only case the fallback exists for
     * @param multiplier how many median missions of history count as recent
     * @param fallback   the window to use when there is no history to scale to
     */
    static Duration from(Optional<Duration> median, int multiplier, Duration fallback) {
        return median.map(duration -> duration.multipliedBy(multiplier)).orElse(fallback);
    }
}
