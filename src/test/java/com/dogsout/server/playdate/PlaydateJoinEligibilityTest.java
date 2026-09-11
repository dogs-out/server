package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Who may turn up at a playdate.
 *
 * <p>The rule exists for a safety reason rather than a tidiness one: a playdate
 * announces a place and a time in public, so it is not something an account with
 * no dog and no sitting role should be able to walk into.
 */
@ExtendWith(MockitoExtension.class)
class PlaydateJoinEligibilityTest {

    // The rule reads only its two arguments, so the collaborators stay null — this
    // keeps the test about the policy rather than about wiring ten mocks.
    private final PlaydateService service = new PlaydateService(
            null, null, null, null, null, null, null, null, null, null);

    private static User user(Boolean hasDog, Boolean isSitter) {
        User u = new User();
        u.setId(1L);
        u.setHasDog(hasDog);
        u.setIsSitter(isSitter);
        return u;
    }

    private static Playdate playdate(Boolean sittersWelcome) {
        Playdate p = new Playdate();
        p.setSittersWelcome(sittersWelcome);
        return p;
    }

    private void check(Playdate playdate, User user) {
        ReflectionTestUtils.invokeMethod(service, "requireEligibleToJoin", playdate, user);
    }

    @Test
    void dogOwnersAreAlwaysWelcome() {
        assertThatCode(() -> check(playdate(false), user(true, false))).doesNotThrowAnyException();
    }

    @Test
    void legacyAccountsWithoutTheFlagCountAsOwners() {
        // hasDog predates some rows; null has always meant "owner" everywhere else.
        assertThatCode(() -> check(playdate(false), user(null, null))).doesNotThrowAnyException();
    }

    @Test
    void sittersMayJoinWhereTheHostLeftThemWelcome() {
        assertThatCode(() -> check(playdate(true), user(false, true))).doesNotThrowAnyException();
    }

    @Test
    void sittersAreWelcomeOnPlaydatesThatPredateTheSetting() {
        // Null means welcome: those playdates were open to everyone when created.
        assertThatCode(() -> check(playdate(null), user(false, true))).doesNotThrowAnyException();
    }

    @Test
    void sittersAreTurnedAwayWhereTheHostSaidNo() {
        assertThatThrownBy(() -> check(playdate(false), user(false, true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not expecting dogsitters");
    }

    @Test
    void someoneWithNeitherRoleCanNeverJoin() {
        assertThatThrownBy(() -> check(playdate(true), user(false, false)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dog owners and dogsitters");
    }
}
