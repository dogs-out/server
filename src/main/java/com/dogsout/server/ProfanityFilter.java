package com.dogsout.server;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ProfanityFilter {

    private static final Set<String> WORDS = Set.of(
        // ── English ──────────────────────────────────────────────────
        "fuck", "fucker", "fucking", "fucked", "fucks",
        "shit", "shitting", "shitty",
        "ass", "asshole", "arsehole",
        "bitch", "bitches",
        "bastard", "cunt", "dick", "cock", "pussy",
        "whore", "slut",
        "nigger", "nigga",
        "faggot", "fag", "kike",
        "retard", "retarded",
        "twat", "wanker", "wank", "prick",
        "motherfucker", "bullshit", "bollocks", "crap",

        // ── German (umlaut-normalized: ä→ae, ö→oe, ü→ue, ß→ss) ──────
        // fuck
        "fick", "ficken", "gefickt", "vogeln", "gevogelt",
        // shit
        "scheisse", "scheiss", "scheisskopf",
        // ass
        "arsch", "arschloch",
        // wanker
        "wichser", "wichse", "wichsen", "wixer",
        // whore / prostitute
        "hure", "hurensohn", "hurentochter", "hurenbock", "nutte",
        // cunt
        "fotze", "votze",
        // slut
        "schlampe",
        // pig
        "drecksau", "schweinhund",
        // slurs
        "schwuchtel", "spasti",
        // misc
        "pisser", "kacke", "kacken", "bumsen"
    );

    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = normalize(text);
        return WORDS.stream().anyMatch(normalized::contains);
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
    }
}
