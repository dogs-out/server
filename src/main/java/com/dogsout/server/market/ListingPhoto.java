package com.dogsout.server.market;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "listing_photos")
@Getter
@Setter
@NoArgsConstructor
public class ListingPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Column(nullable = false)
    private String storageKey;

    private int sortOrder;
}
