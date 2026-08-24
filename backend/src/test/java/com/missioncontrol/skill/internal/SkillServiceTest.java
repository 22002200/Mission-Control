package com.missioncontrol.skill.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.missioncontrol.platform.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * The two decisions the service actually makes: which organisation to scope to, and what a search
 * term turns into.
 *
 * <p>No database. Whether the resulting query returns the right rows is
 * {@link SkillRepositoryIT}'s job; this is about what gets handed to it.
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SKILL_ID = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    @Mock private SkillRepository skills;
    @Mock private CurrentUser currentUser;

    @InjectMocks private SkillService service;

    @Captor private ArgumentCaptor<UUID> organisationCaptor;
    @Captor private ArgumentCaptor<String> patternCaptor;
    @Captor private ArgumentCaptor<Boolean> activeCaptor;
    @Captor private ArgumentCaptor<Pageable> pageableCaptor;

    private static SkillEntity evaOperations() {
        return SkillEntity.builder()
                .id(SKILL_ID)
                .organisationId(ORG)
                .name("EVA Operations")
                .category("Operations")
                .description("Suit handling, tethering, external repair.")
                .active(true)
                .createdAt(Instant.parse("2026-01-05T09:00:00Z"))
                .build();
    }

    @BeforeEach
    void callerIsInOrganisationA() {
        when(currentUser.organisationId()).thenReturn(ORG);
    }

    private void repositoryReturnsNothing() {
        when(skills.findCatalogue(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
    }

    @Test
    @DisplayName("The organisation comes from the token, never from the caller")
    void scopesEveryListToTheCallersOrganisation() {
        repositoryReturnsNothing();

        service.list(null, null, 0, 50);

        verify(skills).findCatalogue(organisationCaptor.capture(), any(), any(), any());
        assertThat(organisationCaptor.getValue()).isEqualTo(ORG).isNotEqualTo(OTHER_ORG);
    }

    @Test
    void anAbsentSearchMeansNoNameFilter() {
        repositoryReturnsNothing();

        service.list(null, null, 0, 50);

        verify(skills).findCatalogue(any(), any(), patternCaptor.capture(), any());
        assertThat(patternCaptor.getValue()).isNull();
    }

    @ParameterizedTest(name = "a search of [{0}] is not a filter")
    @ValueSource(strings = {"", "   "})
    void aBlankSearchMeansNoNameFilter(String blank) {
        repositoryReturnsNothing();

        service.list(null, blank, 0, 50);

        verify(skills).findCatalogue(any(), any(), patternCaptor.capture(), any());
        assertThat(patternCaptor.getValue()).isNull();
    }

    @Test
    void aSearchTermBecomesALowercasedSubstringPattern() {
        repositoryReturnsNothing();

        service.list(null, "  EVA  ", 0, 50);

        verify(skills).findCatalogue(any(), any(), patternCaptor.capture(), any());
        assertThat(patternCaptor.getValue()).isEqualTo("%eva%");
    }

    @Test
    @DisplayName("LIKE wildcards in the search term are escaped, not honoured")
    void wildcardsInTheSearchTermAreEscaped() {
        repositoryReturnsNothing();

        // Unescaped, this would match every skill whose name is at least two characters long.
        service.list(null, "%_", 0, 50);

        verify(skills).findCatalogue(any(), any(), patternCaptor.capture(), any());
        assertThat(patternCaptor.getValue()).isEqualTo("%\\%\\_%");
    }

    @Test
    @DisplayName("A backslash is escaped before the wildcards are, not after")
    void aBackslashIsEscapedFirst() {
        repositoryReturnsNothing();

        // In the other order the escape character itself would be escaped a second time, leaving
        // a dangling escape at the end of the pattern.
        service.list(null, "\\", 0, 50);

        verify(skills).findCatalogue(any(), any(), patternCaptor.capture(), any());
        assertThat(patternCaptor.getValue()).isEqualTo("%\\\\%");
    }

    @Test
    void theActiveFilterIsPassedThroughUntouched() {
        repositoryReturnsNothing();

        service.list(false, null, 0, 50);

        verify(skills).findCatalogue(any(), activeCaptor.capture(), any(), any());
        assertThat(activeCaptor.getValue()).isFalse();
    }

    @Test
    void pagingIsPassedThroughAndReportedBack() {
        // The last page of eight, three at a time: two entries, not three. PageImpl recomputes the
        // total from the content when the two disagree, so an inconsistent fixture would be
        // silently corrected rather than caught.
        when(skills.findCatalogue(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(evaOperations(), evaOperations()), PageRequest.of(2, 3), 8));

        SkillPage page = service.list(null, null, 2, 3);

        verify(skills).findCatalogue(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(8);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).hasSize(2)
                .allSatisfy(skill -> assertThat(skill.name()).isEqualTo("EVA Operations"));
    }

    @Test
    @DisplayName("Sorting belongs to the query, not to a caller-supplied Pageable")
    void theServiceDoesNotAskForASort() {
        repositoryReturnsNothing();

        service.list(null, null, 0, 50);

        verify(skills).findCatalogue(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void getReturnsTheSkillMappedToItsResponse() {
        when(skills.findByIdAndOrganisationId(SKILL_ID, ORG))
                .thenReturn(Optional.of(evaOperations()));

        SkillResponse response = service.get(SKILL_ID);

        assertThat(response.id()).isEqualTo(SKILL_ID);
        assertThat(response.name()).isEqualTo("EVA Operations");
        assertThat(response.category()).isEqualTo("Operations");
        assertThat(response.active()).isTrue();
    }

    @Test
    void getIsScopedToTheCallersOrganisation() {
        when(skills.findByIdAndOrganisationId(eq(SKILL_ID), any()))
                .thenReturn(Optional.of(evaOperations()));

        service.get(SKILL_ID);

        verify(skills).findByIdAndOrganisationId(SKILL_ID, ORG);
    }

    @Test
    @DisplayName("An unknown skill is a 404 whose detail names no id")
    void getRejectsAnUnknownSkill() {
        when(skills.findByIdAndOrganisationId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SKILL_ID))
                .isInstanceOf(SkillNotFoundException.class)
                .satisfies(thrown -> {
                    SkillNotFoundException ex = (SkillNotFoundException) thrown;
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getMessage()).doesNotContain(SKILL_ID.toString());
                });
    }
}
