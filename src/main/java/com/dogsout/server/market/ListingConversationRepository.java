package com.dogsout.server.market;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingConversationRepository extends JpaRepository<ListingConversation, Long> {
    Optional<ListingConversation> findByListingAndBuyer(Listing listing, User buyer);
    List<ListingConversation> findByListing(Listing listing);
    List<ListingConversation> findByBuyer(User buyer);

    @Query("select c from ListingConversation c where c.buyer = :user or c.listing.seller = :user")
    List<ListingConversation> findInvolving(@Param("user") User user);
}
