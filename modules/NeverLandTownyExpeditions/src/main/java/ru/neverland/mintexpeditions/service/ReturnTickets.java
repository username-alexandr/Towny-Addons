package ru.neverland.mintexpeditions.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** A short-lived, manual return remains available after a site has been restored. */
public final class ReturnTickets {
    public record Ticket(UUID campOwner, long expiresAt) {}
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    private final java.util.logging.Logger logger;
    private final File file;

    public ReturnTickets(JavaPlugin plugin) {
        this(new File(plugin.getDataFolder(), "return-tickets.yml"), plugin.getLogger(), System.currentTimeMillis());
    }
    ReturnTickets(File file, java.util.logging.Logger logger, long now) {
        this.file = file; this.logger = logger;
        if (!file.exists()) return;
        var yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            try {
                Ticket ticket = new Ticket(UUID.fromString(yaml.getString(id + ".camp")), yaml.getLong(id + ".expires"));
                if (ticket.expiresAt() > now) tickets.put(UUID.fromString(id), ticket);
            } catch (RuntimeException exception) {
                logger.warning("Пропущено повреждённое право возврата из экспедиции: " + id);
            }
        }
    }

    public Ticket get(UUID player, long now) {
        Ticket ticket = tickets.get(player);
        return ticket != null && ticket.expiresAt() > now ? ticket : null;
    }

    public void grant(Iterable<UUID> players, UUID camp, long expiresAt) {
        for (UUID player : players) tickets.put(player, new Ticket(camp, expiresAt));
        save();
    }

    public void remove(UUID player) { if (tickets.remove(player) != null) save(); }

    public void prune(long now) {
        if (tickets.values().removeIf(ticket -> ticket.expiresAt() <= now)) save();
    }

    private void save() {
        var yaml = new YamlConfiguration();
        tickets.forEach((player, ticket) -> {
            yaml.set(player + ".camp", ticket.campOwner().toString());
            yaml.set(player + ".expires", ticket.expiresAt());
        });
        try { yaml.save(file); }
        catch (IOException exception) { logger.severe("Не удалось сохранить возвраты экспедиций: " + exception.getMessage()); }
    }
}
