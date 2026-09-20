package ru.neverland.townyachievements.model;

public record Progress(Achievement definition, long best, long earnedAt) {
    public Progress {
        if (definition == null || best < 0 || best > definition.target() || earnedAt < 0
                || (earnedAt > 0) != (best == definition.target()))
            throw new IllegalArgumentException("Повреждён прогресс достижения");
    }
    public static Progress begin(Achievement definition) { return new Progress(definition, 0, 0); }
    public boolean earned() { return earnedAt > 0; }
    /** One observation is a simultaneous city state, never a union of buildings across time. */
    public Progress observe(long value, long now) {
        if (value < 0 || now < 1) throw new IllegalArgumentException("Некорректное наблюдение");
        if (earned()) return this;
        long next = Math.max(best, Math.min(definition.target(), value));
        return new Progress(definition, next, next == definition.target() ? now : 0);
    }
}
