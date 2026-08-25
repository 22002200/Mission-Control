package com.missioncontrol.mission.internal;

import java.util.UUID;

/**
 * What a caller wants a required skill to say, before any decision about which row says it.
 *
 * <p>Plain data rather than a detached {@code RequiredSkillEntity}, and that distinction is the
 * whole point. A requirement is saved wholesale, so an edit has to work out which of the existing
 * rows survive; handing the entity a set of freshly-built entities makes 'replace' look like
 * 'delete everything and insert everything', and for a row whose key is unchanged that is not a
 * legal thing to ask a persistence context to do.
 *
 * @param skillId            the catalogue entry, already validated as active and in-tenant
 * @param minimumProficiency 1 to 5, narrowed to match the {@code SMALLINT} column
 * @param mandatory          true makes this a hard filter as well as a scored term
 * @param weight             ranking weight, 1 by default
 */
record RequiredSkillValues(UUID skillId, short minimumProficiency, boolean mandatory, int weight) {
}
