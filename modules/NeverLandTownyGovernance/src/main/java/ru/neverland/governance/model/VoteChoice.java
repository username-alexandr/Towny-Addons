package ru.neverland.governance.model;

import java.util.Locale;

public enum VoteChoice {
    YES, NO, ABSTAIN;

    public static VoteChoice parse(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "yes", "for", "за", "y" -> YES;
            case "no", "against", "против", "n" -> NO;
            case "abstain", "skip", "воздерживаюсь", "воздержался", "a" -> ABSTAIN;
            default -> null;
        };
    }
}
