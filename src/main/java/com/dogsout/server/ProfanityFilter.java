package com.dogsout.server;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ProfanityFilter {

    // Long, unambiguous words — matched as substrings so concatenations
    // ("retardretard") and compounds ("Scheißwetter") are still caught.
    private static final Set<String> SUBSTRING_WORDS = Set.of(
        // ── English ──────────────────────────────────────────────────
        "fuck", "fucker", "fucking", "fucked", "fucks",
        "shitting", "shitty", "shithead",
        "asshole", "arsehole",
        "bitch", "bastard", "cunt",
        "whore", "nigger", "nigga",
        "faggot", "retard",
        "wanker", "motherfucker", "bullshit", "bollocks",

        // ── German (umlaut-normalized: ä→ae, ö→oe, ü→ue, ß→ss) ──────
        "ficken", "gefickt", "verfickt", "gevoegelt",
        "scheiss", "arschloch",
        "wichser", "wichsen", "hurensohn", "hurentochter", "hurenbock",
        "fotze", "votze", "schlampe",
        "drecksau", "schweinhund",
        "schwuchtel", "spasti"
    );

    // Short/ambiguous words — whole-word matches only, so ordinary words
    // like "Wasser", "passt", "Barsch", "Cocker", "schwanken" stay clean.
    private static final Pattern WORD_PATTERN = Pattern.compile(
        "\\b(ass|fag|kike|dick|cock|pussy|slut|twat|wank|prick|crap|shit|"
        + "fick|arsch|wichse|wixer|hure|nutte|kacke|kacken|bumsen|pisser|voegeln)\\b"
    );

    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalize(text);
        return SUBSTRING_WORDS.stream().anyMatch(normalized::contains)
                || WORD_PATTERN.matcher(normalized).find();
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
    }
}
