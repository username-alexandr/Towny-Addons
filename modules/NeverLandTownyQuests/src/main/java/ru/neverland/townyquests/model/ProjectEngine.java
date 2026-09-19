package ru.neverland.townyquests.model;

/** Only observed online intervals count; callers supply zero after any observation gap. */
public final class ProjectEngine {
    private ProjectEngine() {}
    public record Observation(boolean ready, int value, String detail) {
        public Observation { if (value < 0 || detail == null) throw new IllegalArgumentException("Неверное наблюдение"); }
        public static Observation waiting(String reason) { return new Observation(false, 0, reason); }
    }
    public static CityProject advance(CityProject run, Observation observation, int elapsedSeconds) {
        if (elapsedSeconds < 0 || elapsedSeconds > 60) throw new IllegalArgumentException("Интервал: 0..60 секунд");
        if (run.status() != CityProject.Status.ACTIVE || !observation.ready()) return run;
        var stage = run.definition().stages().get(run.stage());
        if (observation.value() < stage.target())
            return new CityProject(run.run(), run.definition(), run.stage(), 0, run.status());
        int held = Math.min(stage.holdSeconds(), run.heldSeconds() + elapsedSeconds);
        if (held < stage.holdSeconds()) return new CityProject(run.run(), run.definition(), run.stage(), held, run.status());
        int next = run.stage() + 1;
        return new CityProject(run.run(), run.definition(), next, 0,
                next == run.definition().stages().size() ? CityProject.Status.COMPLETED : CityProject.Status.ACTIVE);
    }
}
