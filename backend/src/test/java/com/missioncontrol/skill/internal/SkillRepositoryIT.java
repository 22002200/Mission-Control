package com.missioncontrol.skill.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.missioncontrol.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue query against a real PostgreSQL.
 *
 * <p>Three things can only be proved here. That an optional filter written as
 * {@code :active is null or ...} survives contact with PostgreSQL, which refuses a parameter whose
 * type it cannot infer. That the {@code escape} clause is honoured, so a wildcard in a search term
 * is a literal. And that a skill in another organisation is invisible at the query level rather
 * than filtered out afterwards.
 *
 * <p>Transactional, so the inactive fixture below is rolled back. Every other integration test
 * shares this database and counts the seeded catalogue.
 */
@Transactional
class SkillRepositoryIT extends AbstractIntegrationTest {

    private static final UUID ORG_A = UUID.fromString(ORG_A_ID);
    private static final UUID ORG_B = UUID.fromString(ORG_B_ID);

    /** Orbital Dynamics has eight seeded skills, Helios Aerospace six. */
    private static final int ORG_A_SEEDED = 8;
    private static final int ORG_B_SEEDED = 6;

    private static final UUID EVA_IN_ORG_A =
            UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID EVA_IN_ORG_B =
            UUID.fromString("b2000000-0000-0000-0000-000000000001");

    @Autowired private SkillRepository skills;

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 100);

    private SkillEntity insert(UUID organisationId, String name, boolean active) {
        return skills.saveAndFlush(SkillEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(organisationId)
                .name(name)
                .category("Test")
                .active(active)
                .createdAt(Instant.parse("2026-01-05T09:00:00Z"))
                .build());
    }

    private List<String> names(Page<SkillEntity> page) {
        return page.getContent().stream().map(SkillEntity::getName).toList();
    }

    @Test
    @DisplayName("No filters returns the whole of one organisation's catalogue and nothing else")
    void unfilteredReturnsOneOrganisation() {
        assertThat(skills.findCatalogue(ORG_A, null, null, FIRST_PAGE).getTotalElements())
                .isEqualTo(ORG_A_SEEDED);
        assertThat(skills.findCatalogue(ORG_B, null, null, FIRST_PAGE).getTotalElements())
                .isEqualTo(ORG_B_SEEDED);

        assertThat(skills.findCatalogue(ORG_A, null, null, FIRST_PAGE).getContent())
                .allSatisfy(skill -> assertThat(skill.getOrganisationId()).isEqualTo(ORG_A));
    }

    @Test
    void resultsAreSortedByNameCaseInsensitively() {
        insert(ORG_A, "aardvark Handling", true);

        // Ordering the raw column instead of lower(name) would put this last, after Robotics:
        // in a byte-ordered collation every lowercase letter sorts after every uppercase one.
        assertThat(names(skills.findCatalogue(ORG_A, null, null, FIRST_PAGE)))
                .startsWith("aardvark Handling", "Comms and Telemetry", "EVA Operations")
                .endsWith("Robotics");
    }

    @Test
    void theActiveFilterSelectsOnlyMatchingSkills() {
        insert(ORG_A, "Retired Technique", false);

        assertThat(skills.findCatalogue(ORG_A, true, null, FIRST_PAGE).getTotalElements())
                .isEqualTo(ORG_A_SEEDED);
        assertThat(names(skills.findCatalogue(ORG_A, false, null, FIRST_PAGE)))
                .containsExactly("Retired Technique");
        assertThat(skills.findCatalogue(ORG_A, null, null, FIRST_PAGE).getTotalElements())
                .isEqualTo(ORG_A_SEEDED + 1);
    }

    @Test
    @DisplayName("An inactive skill in another organisation is still invisible")
    void theActiveFilterDoesNotCrossTenants() {
        insert(ORG_B, "Retired Elsewhere", false);

        assertThat(skills.findCatalogue(ORG_A, false, null, FIRST_PAGE)).isEmpty();
    }

    @Test
    void theNamePatternMatchesASubstringCaseInsensitively() {
        assertThat(names(skills.findCatalogue(ORG_A, null, "%operations%", FIRST_PAGE)))
                .containsExactly("EVA Operations");
    }

    @Test
    @DisplayName("The pattern matches the name only, never the category")
    void theNamePatternIgnoresTheCategory() {
        // Comms and Telemetry is categorised under Operations but is not named for it.
        assertThat(names(skills.findCatalogue(ORG_A, null, "%operations%", FIRST_PAGE)))
                .doesNotContain("Comms and Telemetry");
    }

    @Test
    @DisplayName("An escaped wildcard in the pattern is a literal character")
    void theEscapeClauseIsHonoured() {
        insert(ORG_A, "Fifty% Duty Cycle", true);
        insert(ORG_A, "Under_score Drill", true);

        assertThat(names(skills.findCatalogue(ORG_A, null, "%\\%%", FIRST_PAGE)))
                .containsExactly("Fifty% Duty Cycle");
        assertThat(names(skills.findCatalogue(ORG_A, null, "%\\_%", FIRST_PAGE)))
                .containsExactly("Under_score Drill");
    }

    @Test
    void bothFiltersApplyTogether() {
        insert(ORG_A, "Retired Operations", false);

        assertThat(names(skills.findCatalogue(ORG_A, true, "%operations%", FIRST_PAGE)))
                .containsExactly("EVA Operations");
        assertThat(names(skills.findCatalogue(ORG_A, false, "%operations%", FIRST_PAGE)))
                .containsExactly("Retired Operations");
    }

    @Test
    void pagingCountsEveryMatchNotJustThePage() {
        Page<SkillEntity> firstOfThree = skills.findCatalogue(
                ORG_A, null, null, PageRequest.of(0, 3));

        assertThat(firstOfThree.getContent()).hasSize(3);
        assertThat(firstOfThree.getTotalElements()).isEqualTo(ORG_A_SEEDED);
        assertThat(firstOfThree.getTotalPages()).isEqualTo(3);

        Page<SkillEntity> lastOfThree = skills.findCatalogue(
                ORG_A, null, null, PageRequest.of(2, 3));

        assertThat(names(lastOfThree)).containsExactly("Propulsion Systems", "Robotics");
    }

    @Test
    @DisplayName("Two organisations hold the same skill name as two separate rows")
    void theSameNameExistsIndependentlyInBothOrganisations() {
        SkillEntity inA = skills.findByIdAndOrganisationId(EVA_IN_ORG_A, ORG_A).orElseThrow();
        SkillEntity inB = skills.findByIdAndOrganisationId(EVA_IN_ORG_B, ORG_B).orElseThrow();

        assertThat(inA.getName()).isEqualTo(inB.getName());
        assertThat(inA.getId()).isNotEqualTo(inB.getId());
    }

    @Test
    void aSkillIsNotFoundThroughAnotherOrganisation() {
        assertThat(skills.findByIdAndOrganisationId(EVA_IN_ORG_B, ORG_A)).isEmpty();
        assertThat(skills.findByIdAndOrganisationId(EVA_IN_ORG_A, ORG_B)).isEmpty();
    }
}
