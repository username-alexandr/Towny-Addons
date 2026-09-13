package ru.neverland.townybuilds.integration;

import java.util.UUID;
import ru.neverland.core.ApiServices;

/** Optional public Events API; an unavailable installed provider must never erase repair debts. */
public final class FireRepairAccess {
    private FireRepairAccess() { }
    public static boolean blocked(UUID town) {
        var api = ApiServices.connect("NeverLandTownyEvents", "ru.neverland.mintevents.api.MintTownyEventsApi", 1, "hasFireDamage");
        if (api.state() == ApiServices.State.NOT_INSTALLED) return false;
        if (!api.ready()) return true;
        try {
            return !Boolean.FALSE.equals(api.invoke("hasFireDamage", new Class<?>[]{UUID.class}, town));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return true;
        }
    }
}
