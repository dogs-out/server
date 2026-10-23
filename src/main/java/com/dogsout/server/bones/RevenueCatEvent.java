package com.dogsout.server.bones;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A RevenueCat webhook already applied. RevenueCat retries, and a bone pack must pay out once. */
@Entity
@Table(name = "revenuecat_events")
@Getter
@Setter
@NoArgsConstructor
public class RevenueCatEvent {

    @Id
    private String eventId;

    private Long userId;

    private String type;

    private String productId;

    private Instant receivedAt;
}
