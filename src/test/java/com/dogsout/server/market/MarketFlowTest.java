package com.dogsout.server.market;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.market.MarketDtos.ListingRequest;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.moderation.ReportRequest;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The marketplace end to end, against a real (H2) database so foreign keys are enforced. */
@SpringBootTest
@Transactional
class MarketFlowTest {

    @Autowired MarketService market;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired MatchRepository matches;
    @Autowired ListingRepository listings;
    @MockitoBean PushNotificationService push;
    @MockitoBean EmailService email;

    User user(String name, double lat, double lng) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "@market.test");
        u.setName(name);
        u.setPassword("x");
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        u.setLatitude(lat);
        u.setLongitude(lng);
        return users.save(u);
    }

    @Test
    void listAskAnswerWithoutAMatch() {
        User lea = user("Lea", 47.43, 8.57), moritz = user("Moritz", 47.40, 8.55);
        var listing = market.create(lea.getEmail(), new ListingRequest("Hundebett gross", "Kaum gebraucht", 2500, "BED"));

        var seen = market.browse(moritz.getEmail(), null);
        assertThat(seen).extracting(MarketDtos.ListingResponse::title).containsExactly("Hundebett gross");
        assertThat(seen.get(0).priceRappen()).isEqualTo(2500);
        // Her own listing is not in her browse list, only under "mine".
        assertThat(market.browse(lea.getEmail(), null)).isEmpty();

        var convo = market.openConversation(moritz.getEmail(), listing.id());
        market.send(moritz.getEmail(), convo.id(), "Ist das noch da?");
        var leasView = market.conversations(lea.getEmail());
        assertThat(leasView).hasSize(1);
        assertThat(leasView.get(0).unread()).isTrue();
        assertThat(leasView.get(0).iAmSeller()).isTrue();

        market.send(lea.getEmail(), convo.id(), "Ja, Samstag abholen?");
        assertThat(market.messages(moritz.getEmail(), convo.id())).hasSize(2);
        // A conversation is not a match.
        assertThat(matches.findAllMatchesForUser(lea.getId())).isEmpty();
    }

    @Test
    void farAwayListingsAreNotShown() {
        User lea = user("Lea", 47.43, 8.57), geneva = user("Genf", 46.20, 6.14);
        market.create(lea.getEmail(), new ListingRequest("Leine", null, null, "LEASH_COLLAR"));
        assertThat(market.browse(geneva.getEmail(), null)).isEmpty();
    }

    @Test
    void aReportedListingDisappearsAtOnce() {
        User lea = user("Lea", 47.43, 8.57), moritz = user("Moritz", 47.40, 8.55);
        var listing = market.create(lea.getEmail(), new ListingRequest("Welpe", null, 50000, "OTHER"));
        market.report(moritz.getEmail(), listing.id(), new ReportRequest("Live animal", null));

        assertThat(market.browse(moritz.getEmail(), null)).isEmpty();
        assertThatThrownBy(() -> market.get(moritz.getEmail(), listing.id())).isInstanceOf(ResponseStatusException.class);
        // The seller still sees it, to know what happened.
        assertThat(market.mine(lea.getEmail())).hasSize(1);
    }

    @Test
    void unknownCategoriesAndInsultsAreRefused() {
        User lea = user("Lea", 47.43, 8.57);
        assertThatThrownBy(() -> market.create(lea.getEmail(), new ListingRequest("Auto", null, 100, "CARS")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> market.create(lea.getEmail(), new ListingRequest("Scheissleine", null, 100, "LEASH_COLLAR")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletingAnAccountTakesItsListingsAndConversations() {
        User lea = user("Lea", 47.43, 8.57), moritz = user("Moritz", 47.40, 8.55);
        var listing = market.create(lea.getEmail(), new ListingRequest("Napf", null, null, "CARE"));
        var convo = market.openConversation(moritz.getEmail(), listing.id());
        market.send(moritz.getEmail(), convo.id(), "Gratis? Super!");

        userService.deleteAccount(lea.getEmail());
        assertThat(listings.findAll()).isEmpty();
        assertThat(market.conversations(moritz.getEmail())).isEmpty();

        // And the other way round: a buyer's account goes cleanly too.
        User anna = user("Anna", 47.41, 8.56);
        var second = market.create(anna.getEmail(), new ListingRequest("Spielzeug", null, 500, "TOYS"));
        var c2 = market.openConversation(moritz.getEmail(), second.id());
        market.send(moritz.getEmail(), c2.id(), "Noch da?");
        userService.deleteAccount(moritz.getEmail());
        assertThat(market.conversations(anna.getEmail())).isEmpty();
    }
}
