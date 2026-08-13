package com.dogsout.server.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceDisplayNameTest {

    @Test
    void prefersTheNameAppleSupplied() {
        assertThat(AuthService.displayName("Lena Frei", "lena@example.com")).isEqualTo("Lena Frei");
    }

    @Test
    void fallsBackToTheEmailLocalPartWhenAppleSendsNoName() {
        // Apple only offers the name on the first authorization, so this is the
        // normal case for every subsequent sign-in.
        assertThat(AuthService.displayName(null, "lena@example.com")).isEqualTo("lena");
        assertThat(AuthService.displayName("   ", "lena@example.com")).isEqualTo("lena");
    }

    /**
     * The reason the name is worth forwarding at all: a Hide My Email address has no
     * human-readable local part, so the fallback produces a random-looking name.
     */
    @Test
    void usesTheRealNameForHideMyEmailAccounts() {
        String relay = "a1b2c3d4e5@privaterelay.appleid.com";

        assertThat(AuthService.displayName("Jonas Widmer", relay)).isEqualTo("Jonas Widmer");
        assertThat(AuthService.displayName(null, relay)).isEqualTo("a1b2c3d4e5");
    }

    @Test
    void trimsAndCapsAnOverlongName() {
        String longName = "x".repeat(200);

        assertThat(AuthService.displayName("  Mira Sutter  ", "m@example.com")).isEqualTo("Mira Sutter");
        assertThat(AuthService.displayName(longName, "m@example.com")).hasSize(100);
    }
}
