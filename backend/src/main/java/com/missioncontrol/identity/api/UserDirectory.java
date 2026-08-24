package com.missioncontrol.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Turning user ids into names, for modules that reference a user without owning one.
 *
 * <p>The first published type in this module, and the moment its package documentation predicted.
 * {@code mission} stores {@code missionLeadId} as a bare UUID - no foreign key is allowed across a
 * module boundary - yet every mission response has to say who leads it.
 *
 * <p>Bulk only. A director's mission list spans every lead in the organisation, so resolving one
 * name per mission is an N+1 that grows with the page. Feature 04's NFR-1 rules that out, and
 * there is no single-id method here because adding one would make the N+1 easy to write by
 * accident.
 *
 * <p>The organisation is a parameter rather than something this module reads from the security
 * context, so the tenant is visible in the calling code. Ids outside it are absent from the
 * result - invariant T2 - which also means a caller cannot use this to confirm that an id from
 * another organisation exists.
 */
public interface UserDirectory {

    /**
     * Looks up several users at once.
     *
     * @return those that exist in that organisation, keyed by id; unknown ids are absent. An empty
     *         input yields an empty map without touching the database.
     */
    Map<UUID, UserSummary> findByIds(Collection<UUID> userIds, UUID organisationId);
}
