package ru.neverland.core;

import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;

/** Uses the Events shield ledger; the registration fallback also supports standalone addons. */
public final class NewcomerProtection {
    private NewcomerProtection() { }
    public static long registrationRemaining(long registered, long now) {
        if (registered <= 0) return 0;
        if (registered < 100_000_000_000L) registered = Math.multiplyExact(registered, 1000);
        return Math.max(0, Math.subtractExact(Math.addExact(registered, 86_400_000L), now));
    }
    public static long remaining(JavaPlugin consumer, UUID town) {
        ApiServices.primaryThread();
        if (!consumer.getConfig().getBoolean("newcomer-protection.enabled", true)) return 0;
        var c = ApiServices.connect("NeverLandTownyEvents", "ru.neverland.mintevents.api.MintTownyEventsApi", 1, "shieldRemainingMillis");
        if (c.state() == ApiServices.State.NOT_INSTALLED) {
            var city = TownyAPI.getInstance().getTown(town);
            return city == null ? 0 : registrationRemaining(city.getRegistered(), System.currentTimeMillis());
        }
        if (!c.ready()) throw new IllegalStateException("Защита новичков временно недоступна");
        try {
            Object value = c.invoke("shieldRemainingMillis", new Class<?>[]{UUID.class}, town);
            if (value instanceof Long millis && millis >= 0) return millis;
            throw new IllegalStateException("Некорректный срок щита новичка");
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Защита новичков временно недоступна", ex);
        }
    }
}
