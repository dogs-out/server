package com.dogsout.server;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProfanityFilterTest {

    private final ProfanityFilter filter = new ProfanityFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "Hey Lust auf ein Playdate?",
            "Es ist schon hell draussen",
            "Unser Rüde ist dick geworden",
            "Sie ist ein Cocker Spaniel",
            "Das Wasser passt"})
    void letsEverydayTextThrough(String text) {
        assertThat(filter.containsProfanity(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Du Arschloch", "fuck off", "what a bitch", "Scheisswetter"})
    void stillCatchesInsults(String text) {
        assertThat(filter.containsProfanity(text)).isTrue();
    }
}
