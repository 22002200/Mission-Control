/**
 * The shared kernel: types genuinely shared by more than one module.
 *
 * <p>Intentionally empty. Nothing has earned a place here yet, and premature sharing is how a
 * modular monolith quietly turns back into a big ball of mud. Add a type here only once a second
 * module actually needs it - not in anticipation.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN} so any module
 * may depend on it without an explicit allow-list entry.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel"
)
package com.missioncontrol.shared;
