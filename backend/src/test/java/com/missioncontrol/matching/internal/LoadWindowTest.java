package com.missioncontrol.matching.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How far back an assignment counts as recent - the window half of BR-8.
 *
 * <p>Worth its own test because the failure mode is invisible. A window that quietly collapsed to
 * nothing would not throw; the load penalty would simply stop discriminating between candidates,
 * and the rankings would still look entirely plausible.
 */
class LoadWindowTest {

    private static final Duration FALLBACK = Duration.ofDays(365);

    @Test
    @DisplayName("The window is a multiple of the organisation's median mission length")
    void scalesToTheOrganisationsOwnTempo() {
        assertThat(LoadWindow.from(Optional.of(Duration.ofDays(14)), 6, FALLBACK))
                .isEqualTo(Duration.ofDays(84));
    }

    /**
     * The reason the multiplier exists at all. A window one mission long finds almost nobody
     * recently loaded, so the penalty stops participating in the ranking without ever failing.
     */
    @Test
    @DisplayName("Two organisations with different tempos get different windows")
    void differentTemposGiveDifferentWindows() {
        Duration sorties = LoadWindow.from(Optional.of(Duration.ofDays(3)), 6, FALLBACK);
        Duration expeditions = LoadWindow.from(Optional.of(Duration.ofDays(180)), 6, FALLBACK);

        assertThat(sorties).isEqualTo(Duration.ofDays(18));
        assertThat(expeditions).isEqualTo(Duration.ofDays(1080));
    }

    @Test
    @DisplayName("An organisation that has completed no missions falls back to the default")
    void fallsBackWithNoHistory() {
        assertThat(LoadWindow.from(Optional.empty(), 6, FALLBACK)).isEqualTo(FALLBACK);
    }

    /**
     * There is deliberately no floor and no ceiling. A clamp exists to defend against outliers
     * dragging a mean, and a median is not dragged - so the guard has nothing left to guard.
     */
    @Test
    @DisplayName("A very short or very long median is used as it stands, unclamped")
    void appliesNoFloorOrCeiling() {
        assertThat(LoadWindow.from(Optional.of(Duration.ofHours(6)), 6, FALLBACK))
                .isEqualTo(Duration.ofHours(36));
        assertThat(LoadWindow.from(Optional.of(Duration.ofDays(1000)), 6, FALLBACK))
                .isEqualTo(Duration.ofDays(6000));
    }
}
