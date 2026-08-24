/**
 * Crew profiles and what each crew member is rated at.
 *
 * <p>Owns {@code CrewMember} and {@code CrewSkill}. A crew member is deliberately thin - an id, an
 * organisation and a link to the {@code identity} user who logs in as them. The substance is the
 * skill ratings, and everything else about a crew member is derived: availability and assignment
 * history both come from assignments, which this module does not own and does not read.
 *
 * <p>{@code userId} is a bare UUID with no foreign key. The account belongs to {@code identity} and
 * architecture.md forbids a constraint across a module boundary, so nothing here can resolve a
 * name. That is on purpose: a caller who needs one asks {@code identity} for it, and this module
 * never learns what a person is called.
 *
 * <p>The allow-list is empty, which is unusual enough to explain. There is no controller here -
 * feature 06 reads the roster through {@link com.missioncontrol.crew.api.CrewDirectory} and
 * nothing else - so no type in this module ever needs {@code CurrentUser}, and the organisation
 * arrives as a parameter rather than being read from the security context. The moment crew members
 * can edit their own profiles this gains {@code platform}, and that will be one line.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Crew")
package com.missioncontrol.crew;
