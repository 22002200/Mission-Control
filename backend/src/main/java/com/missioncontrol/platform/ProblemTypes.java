package com.missioncontrol.platform;

import java.net.URI;

/**
 * The {@code type} URNs shared across modules.
 *
 * <p>Only the generic ones live here. A module that has an error of its own - {@code identity} and
 * its invalid-credentials, for instance - declares that URN next to the exception that raises it,
 * so {@code platform} stays free of domain vocabulary.
 */
public final class ProblemTypes {

    public static final URI VALIDATION_FAILED = URI.create("urn:mission-control:validation-failed");
    public static final URI UNAUTHENTICATED = URI.create("urn:mission-control:unauthenticated");
    public static final URI FORBIDDEN = URI.create("urn:mission-control:forbidden");
    public static final URI NOT_FOUND = URI.create("urn:mission-control:not-found");
    public static final URI METHOD_NOT_ALLOWED =
            URI.create("urn:mission-control:method-not-allowed");
    public static final URI INTERNAL_ERROR = URI.create("urn:mission-control:internal-error");

    private ProblemTypes() {
    }
}
