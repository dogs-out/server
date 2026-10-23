package com.dogsout.server.bones;

import java.time.Instant;

/** What the app shows on the bone counter. */
public record BoneStatus(
        /** Bones left right now: what is left of today's allowance plus any extra ones. */
        int remaining,
        /** Today's allowance: 10, or 20 with Premium. */
        int dailyAllowance,
        /** Extra bones from ads or purchases, already counted in remaining. */
        int bonus,
        /** Next midnight in Switzerland, when the allowance refills. */
        Instant refillsAt,
        boolean premium,
        /** How many more bones an ad can earn today. */
        int adRewardsLeft
) {}
