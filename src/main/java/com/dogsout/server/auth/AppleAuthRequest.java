package com.dogsout.server.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * @param fullName the name Apple hands the app, if any. Apple returns it <em>only</em>
 *                 on a user's very first authorization and never again, so it has to be
 *                 captured then or the account is stuck with a name derived from the
 *                 email address forever. Never used to identify the user — that is
 *                 always the verified {@code sub} in the identity token.
 */
public record AppleAuthRequest(@NotBlank String identityToken, String fullName) {}