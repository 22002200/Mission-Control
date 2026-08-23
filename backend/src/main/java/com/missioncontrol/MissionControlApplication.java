package com.missioncontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

/**
 * Mission Control - a modular monolith for planning space missions and assigning crew.
 *
 * <p>Classes in this root package are visible to every application module. Everything else
 * belongs inside a module package (a direct sub-package of this one). See
 * {@code docs/architecture.md} for the module rules and the checklist for adding one.
 */
@Modulith
@SpringBootApplication
public class MissionControlApplication {

    public static void main(String[] args) {
        SpringApplication.run(MissionControlApplication.class, args);
    }
}
