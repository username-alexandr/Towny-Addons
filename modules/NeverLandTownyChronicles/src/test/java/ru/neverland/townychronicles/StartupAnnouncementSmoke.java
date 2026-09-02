package ru.neverland.townychronicles;

import ru.neverland.townychronicles.service.StartupAnnouncementPolicy;

public final class StartupAnnouncementSmoke {
    public static void main(String[] args) {
        check(!StartupAnnouncementPolicy.shouldAnnounce(false, true),
                "Существующее Чудо не должно объявляться при запуске");
        check(!StartupAnnouncementPolicy.shouldAnnounce(true, false),
                "Первичное состояние нового города не должно объявляться как достижение");
        check(StartupAnnouncementPolicy.shouldAnnounce(false, false),
                "Реальное событие после запуска должно объявляться");
        check(StartupAnnouncementPolicy.shouldRecordWonder(true, true, true),
                "Существующее Чудо должно попасть в историю при включённой настройке");
        check(!StartupAnnouncementPolicy.shouldRecordWonder(true, true, false),
                "Существующее Чудо не должно записываться при отключённой настройке");
        check(StartupAnnouncementPolicy.shouldRecordWonder(true, false, false),
                "Новое Чудо после запуска должно записываться всегда");
        System.out.println("StartupAnnouncementSmoke OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
