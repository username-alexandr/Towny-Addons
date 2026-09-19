package ru.neverland.townyquests.model;

import java.util.*;

public record CityProject(UUID run, Project definition, int stage, int heldSeconds, Status status) {
    public enum Status { ACTIVE, PAUSED, CANCELLED, COMPLETED }
    public CityProject {
        Objects.requireNonNull(run); Objects.requireNonNull(definition); Objects.requireNonNull(status);
        if (stage < 0 || stage > definition.stages().size() || heldSeconds < 0
                || (status == Status.COMPLETED) != (stage == definition.stages().size())
                || (status == Status.COMPLETED ? heldSeconds != 0 : heldSeconds > definition.stages().get(stage).holdSeconds()))
            throw new IllegalArgumentException("Повреждён прогресс городского проекта");
    }
    public static CityProject start(Project definition) { return new CityProject(UUID.randomUUID(), definition, 0, 0, Status.ACTIVE); }
    public CityProject control(String action) {
        if (status == Status.COMPLETED) throw new IllegalArgumentException("Проект завершён; результат уже открыт");
        return switch (action) {
            case "pause" -> new CityProject(run, definition, stage, heldSeconds, Status.PAUSED);
            case "resume" -> new CityProject(run, definition, stage, heldSeconds, Status.ACTIVE);
            case "restart" -> new CityProject(run, definition, stage, 0, status);
            case "cancel" -> new CityProject(run, definition, stage, heldSeconds, Status.CANCELLED);
            default -> throw new IllegalArgumentException("Неизвестное действие");
        };
    }
}
