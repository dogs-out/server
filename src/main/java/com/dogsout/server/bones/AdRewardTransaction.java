package com.dogsout.server.bones;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An AdMob reward already paid out. Google may deliver the same callback more than
 * once, and one watched ad must stay one bone.
 */
@Entity
@Table(name = "ad_reward_transactions")
@Getter
@Setter
@NoArgsConstructor
public class AdRewardTransaction {

    @Id
    private String transactionId;

    private Long userId;

    private Instant createdAt;
}
