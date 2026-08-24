package com.missioncontrol.platform;

/**
 * Names of the private claims Mission Control puts on its tokens.
 *
 * <p>Deliberately package-private in spirit: only {@link JwtTokenIssuer} writes them and only
 * {@link SecurityContextCurrentUser} reads them, so no domain module ever parses a claim by hand.
 * They are public because the identity module's token validator needs {@link #ISSUED_AT_MILLIS}.
 */
public final class JwtClaims {

    /** Organisation the subject belongs to. Scopes every query the request goes on to make. */
    public static final String ORGANISATION_ID = "org";

    /** The subject's single {@link com.missioncontrol.shared.UserRole}, by name. */
    public static final String ROLE = "role";

    /**
     * Issue time in epoch <strong>milliseconds</strong>.
     *
     * <p>The standard {@code iat} claim is a NumericDate - whole seconds - but {@code
     * app_user.tokens_valid_from} is a microsecond-precision timestamp. Comparing the two directly
     * is wrong in both directions: truncating {@code iat} down means a token minted moments after a
     * logout looks older than the logout and is rejected, while truncating the logout instant down
     * means the very token used to log out survives it. Neither is acceptable, so revocation
     * compares against this claim instead and {@code iat} is kept only for convention.
     *
     * <p>It also makes two logins within the same second produce different tokens, which is what
     * lets 'a different token issued earlier is also rejected' be tested at all.
     */
    public static final String ISSUED_AT_MILLIS = "iat_ms";

    private JwtClaims() {
    }
}
