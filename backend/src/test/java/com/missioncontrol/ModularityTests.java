package com.missioncontrol;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the module boundaries described in {@code docs/architecture.md}.
 *
 * <p>This is the guardrail the whole modular-monolith approach rests on. Without it the boundaries
 * are a convention that erodes the first time someone is in a hurry; with it, reaching into
 * another module's {@code internal} package fails the build.
 *
 * <p>No Spring context and no database are involved - Spring Modulith analyses the compiled
 * bytecode with ArchUnit, so this runs in about a second.
 */
class ModularityTests {

    private static final Logger log = LoggerFactory.getLogger(ModularityTests.class);

    private static final ApplicationModules MODULES =
            ApplicationModules.of(MissionControlApplication.class);

    /**
     * Fails if any module reaches into another module's internals, if a module depends on one it
     * has not declared, or if the package structure is cyclic.
     *
     * <p>Note that a module declared {@code @ApplicationModule(type = OPEN)} exposes everything by
     * design, so violations against it cannot be detected. Both current modules ({@code platform}
     * and {@code shared}) are open infrastructure, which means this test has little to bite on
     * today. It becomes load-bearing the moment the first closed domain module lands - which is
     * exactly why it is here before that happens rather than after.
     */
    @Test
    void verifiesModularStructure() {
        MODULES.forEach(module -> log.info("Detected module: {}", module.getDisplayName()));

        MODULES.verify();
    }

    /**
     * Writes C4-style component diagrams and a module canvas per module into
     * {@code target/spring-modulith-docs}.
     *
     * <p>Generated from the code rather than maintained by hand, so it cannot drift out of date
     * the way an architecture diagram in a wiki does.
     */
    @Test
    void writesDocumentationSnippets() {
        new Documenter(MODULES).writeDocumentation();
    }
}
