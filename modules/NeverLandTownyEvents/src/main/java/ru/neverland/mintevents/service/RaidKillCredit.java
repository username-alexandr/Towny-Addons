package ru.neverland.mintevents.service;

import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventMode;

public final class RaidKillCredit {
    private RaidKillCredit() {}

    public static int points(ActiveEvent event, EventMode mode, boolean playerKill, int configured, long now) {
        if (event == null || mode != EventMode.RAID || !playerKill
                || event.endsAt() <= now || event.completed()) return 0;
        return Math.min(Math.max(0, configured), event.goal() - event.progress());
    }
}
