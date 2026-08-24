/**
 * The shared kernel: types genuinely shared by more than one module.
 *
 * <p>Currently holds exactly one type, {@link com.missioncontrol.shared.UserRole}. It is here
 * because {@code platform} must be able to describe the role of an authenticated caller, and
 * {@code platform} cannot depend on {@code identity} without creating a cycle - see that type's
 * javadoc. Several domain modules need to name a role as well, so it clears the bar below rather
 * than being parked here for convenience.
 *
 * <p>Add a type here only once a second module actually needs it - not in anticipation. Premature
 * sharing is how a modular monolith quietly turns back into a big ball of mud.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} so any module
 * may depend on it without an explicit allow-list entry.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel"
)
package com.missioncontrol.shared;
