package ru.neverland.townycitizens.model;

public enum CitizenRight {
    BUILD("Строительство"), DESTROY("Снос блоков"), VOTE("Голосование"), HOLD_OFFICE("Городская должность"),
    STORAGE("Муниципальный склад"), SHOP("Городская лавка"),
    STABLES("Городские конюшни"), INSURANCE("Страхование");
    private final String title;
    CitizenRight(String title) { this.title = title; }
    public String title() { return title; }
}
