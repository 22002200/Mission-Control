package com.missioncontrol.matching.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The numbers the ranking is built out of.
 *
 * <p>Configuration rather than literals buried in the algorithm - feature 06's NFR-6. Every one of
 * these encodes a judgement about how an organisation values experience against availability, and
 * a judgement that can only be changed by editing an expression and rebuilding is not really a
 * judgement anybody can revisit.
 *
 * <p>Every field is validated, so a nonsensical deployment fails at startup rather than producing
 * quietly wrong rankings that nobody can distinguish from correct ones.
 *
 * @param experienceBonusPerMission   added per completed mission
 * @param experienceBonusCap          the most experience can contribute
 * @param loadPenaltyPerAssignment    subtracted per recent or upcoming assignment
 * @param loadPenaltyCap              the most load can subtract
 * @param loadWindowMultiplier        how many median missions of history count as recent
 * @param defaultLoadWindow           the window for an organisation that has completed no missions
 * @param defaultLimit                candidates returned when the caller does not say
 * @param maxLimit                    the most a caller may ask for
 * @param maxExcluded                 the most crew member ids an exclusion list may carry
 */
@Validated
@ConfigurationProperties(prefix = "missioncontrol.matching")
public record MatchingProperties(

        @PositiveOrZero
        double experienceBonusPerMission,

        @PositiveOrZero
        double experienceBonusCap,

        @PositiveOrZero
        double loadPenaltyPerAssignment,

        // Both caps are stated as positive magnitudes and applied with the right sign at the point
        // of use. A negative number in configuration that then gets subtracted is the kind of
        // double negative that reads correctly and behaves backwards.
        @PositiveOrZero
        double loadPenaltyCap,

        /*
         * Six median missions, not one. A window as long as a single mission would find almost
         * nobody recently loaded, and a penalty that never fires is not a penalty - it is a term
         * that quietly stopped participating in the ranking.
         */
        @Positive
        int loadWindowMultiplier,

        @NotNull
        Duration defaultLoadWindow,

        @Min(1) @Max(50)
        int defaultLimit,

        @Min(1) @Max(50)
        int maxLimit,

        // Fifty is about sixteen rematches at the default limit of three, which is far past any
        // real session. It is a bound on the query string rather than a product rule.
        @Min(1) @Max(500)
        int maxExcluded) {
}
