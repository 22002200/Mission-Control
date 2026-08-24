package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.missioncontrol.mission.api.StaffingReadModel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * The optional seam to the assignment module.
 *
 * <p>Worth its own test because the failure mode is silent. If the fallback stopped being used, or
 * a contributed bean stopped being picked up, nothing would throw - missions would simply report
 * the wrong staffing, which reads as a data problem rather than a wiring one.
 */
class MissionStaffingTest {

    private static final UUID CREW = UUID.randomUUID();
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID REQUIREMENT = UUID.randomUUID();

    @Test
    @DisplayName("With no assignment module, nothing is staffed and nobody is assigned")
    void fallsBackToTheNoOpWhenNothingIsContributed() {
        MissionStaffing staffing = new MissionStaffing(
                new DefaultListableBeanFactory().getBeanProvider(StaffingReadModel.class));

        assertThat(staffing.acceptedCounts(List.of(REQUIREMENT))).isEmpty();
        assertThat(staffing.missionIdsAssignedTo(CREW, ORG)).isEmpty();
    }

    @Test
    @DisplayName("A contributed read model is used in preference to the fallback")
    void prefersAContributedReadModel() {
        UUID mission = UUID.randomUUID();
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("assignments", new StaffingReadModel() {
            @Override
            public Map<UUID, Integer> acceptedCountsByRequirement(
                    java.util.Collection<UUID> requirementIds) {
                return Map.of(REQUIREMENT, 3);
            }

            @Override
            public Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId) {
                return Set.of(mission);
            }
        });

        MissionStaffing staffing =
                new MissionStaffing(factory.getBeanProvider(StaffingReadModel.class));

        assertThat(staffing.acceptedCounts(List.of(REQUIREMENT))).containsEntry(REQUIREMENT, 3);
        assertThat(staffing.missionIdsAssignedTo(CREW, ORG)).containsExactly(mission);
    }

    @Test
    @DisplayName("An empty request short-circuits rather than asking for the counts of nothing")
    void emptyRequestIsNotDelegated() {
        StaffingReadModel readModel = mock(StaffingReadModel.class);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("assignments", readModel);

        MissionStaffing staffing =
                new MissionStaffing(factory.getBeanProvider(StaffingReadModel.class));

        assertThat(staffing.acceptedCounts(List.of())).isEmpty();
        verify(readModel, never()).acceptedCountsByRequirement(anyCollection());
    }

    @Test
    @DisplayName("The provider is consulted per call, not captured once in the constructor")
    void resolvesTheReadModelOnEveryCall() {
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<StaffingReadModel> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(new UnstaffedReadModel());

        MissionStaffing staffing = new MissionStaffing(provider);
        staffing.missionIdsAssignedTo(CREW, ORG);
        staffing.missionIdsAssignedTo(CREW, ORG);

        verify(provider, org.mockito.Mockito.times(2)).getIfAvailable(any());
    }
}
