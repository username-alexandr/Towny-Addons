package ru.neverland.reputation.util;

public final class ReputationMath {
    private ReputationMath() { }
    public static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    public static int decay(int score, double percent, int minimumStep) {
        if (score == 0) return 0; int step = Math.max(Math.max(1, minimumStep), (int) Math.round(Math.abs(score) * Math.max(0, percent) / 100.0));
        return score > 0 ? Math.max(0, score - step) : Math.min(0, score + step);
    }
}
