package com.missioncontrol.identity.internal;

import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The directory's published lookup.
 *
 * <p>Kept apart from {@link AuthenticationService}, which is about proving who someone is. This is
 * about describing someone to a third party, and the two have no reason to share a lifetime or a
 * transaction.
 */
@Component
class UserDirectoryLookup implements UserDirectory {

    private final UserRepository users;

    UserDirectoryLookup(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findByIds(Collection<UUID> userIds, UUID organisationId) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        return users.findSummaries(userIds, organisationId).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }
}
