package com.dogsout.server.market;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    List<Listing> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<Listing> findBySellerOrderByCreatedAtDesc(User seller);
}
