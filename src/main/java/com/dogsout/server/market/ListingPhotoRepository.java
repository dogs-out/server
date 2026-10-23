package com.dogsout.server.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {
    List<ListingPhoto> findByListingOrderBySortOrderAsc(Listing listing);
}
