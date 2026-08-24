package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionTempo;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The published median, over the same repository the mission endpoints use.
 *
 * <p>Its own bean rather than another method on {@code MissionService}, following
 * {@code SkillCatalogueLookup}: that service answers HTTP and returns responses, this answers
 * another module and returns a {@link Duration}.
 *
 * <p>Deliberately not cached. One indexed aggregate over the completed missions of a single
 * organisation is cheap, and a cache would buy staleness that somebody then has to reason about
 * every time a mission closes. If profiling ever asks for one, that is the moment to add it and
 * not before.
 */
@Component
class MissionTempoLookup implements MissionTempo {

    private final MissionRepository missions;

    MissionTempoLookup(MissionRepository missions) {
        this.missions = missions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Duration> medianCompletedMissionDuration(UUID organisationId) {
        Double seconds = missions.findMedianCompletedDurationSeconds(
                organisationId, MissionStatus.CLOSED.code(), MissionCloseReason.COMPLETED.code());

        // Null means no completed missions at all. A zero-length median would mean something
        // different and is not reachable anyway - invariant M1 puts endsAt after startsAt - so the
        // two do not have to be told apart here.
        return Optional.ofNullable(seconds).map(value -> Duration.ofSeconds(Math.round(value)));
    }
}
