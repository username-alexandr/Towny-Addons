package ru.neverland.mintcamps.service;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.util.ColorUtil;
import ru.neverland.mintcamps.util.TimeUtil;

import java.util.Map;
import java.util.UUID;

public final class HologramService implements Listener {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final MessageService messages;
    private final CampService camps;
    private final NamespacedKey ownerKey;
    private final NamespacedKey legacyOwnerKey;
    private BukkitTask task;

    public HologramService(JavaPlugin plugin, CampRepository repository, MessageService messages, CampService camps) {
        this.plugin = plugin;
        this.repository = repository;
        this.messages = messages;
        this.camps = camps;
        this.ownerKey = new NamespacedKey(plugin, "camp_owner");
        this.legacyOwnerKey = NamespacedKey.fromString("minttownycamps:camp_owner");
    }

    public void start() {
        stopTask();
        cleanupLoadedHolograms();
        if (!plugin.getConfig().getBoolean("settings.hologram.enabled", true)) {
            for (Camp camp : repository.all()) remove(camp);
            repository.saveIfDirty();
            return;
        }
        long ticks = Math.max(10L, plugin.getConfig().getLong("settings.hologram.update-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, ticks);
    }

    public void updateAll() {
        for (Camp camp : repository.all()) {
            camps.updateCampfire(camp);
            TextDisplay display = resolve(camp);
            if (display == null) display = spawn(camp);
            if (display != null) update(display, camp);
        }
    }

    public void remove(Camp camp) {
        UUID hologramId = camp.hologramId();
        if (hologramId != null) {
            Entity entity = Bukkit.getEntity(hologramId);
            if (entity != null) entity.remove();
        }
        World world = camp.world().orElse(null);
        Location anchor = camp.anchorLocation();
        if (world != null && anchor != null
                && world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) {
            removeOwnedDisplays(world.getChunkAt(anchor), camp.ownerId());
        }
        camp.hologramId(null);
        repository.markDirty();
    }

    public void stop(boolean removeDisplays) {
        stopTask();
        if (removeDisplays) for (Camp camp : repository.all()) remove(camp);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.getConfig().getBoolean("settings.hologram.enabled", true)) reconcileChunk(event.getChunk());
        else removePluginDisplays(event.getChunk());
        repository.saveIfDirty();
    }

    private TextDisplay resolve(Camp camp) {
        if (camp.hologramId() != null) {
            Entity entity = Bukkit.getEntity(camp.hologramId());
            if (entity instanceof TextDisplay text && entity.isValid() && belongsTo(text, camp)) return text;
            camp.hologramId(null);
            repository.markDirty();
        }
        World world = camp.world().orElse(null);
        Location anchor = camp.anchorLocation();
        if (world == null || anchor == null || !world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) return null;
        for (Entity entity : world.getNearbyEntities(anchor, 4, 6, 4)) {
            String stored = storedOwner(entity);
            if (entity instanceof TextDisplay text && camp.ownerId().toString().equals(stored)) {
                camp.hologramId(text.getUniqueId());
                return text;
            }
        }
        return null;
    }

    private void cleanupLoadedHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) reconcileChunk(chunk);
        }
        repository.saveIfDirty();
    }

    private void reconcileChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof TextDisplay display)) continue;
            String stored = storedOwner(display);
            if (stored == null) continue;
            UUID ownerId;
            try {
                ownerId = UUID.fromString(stored);
            } catch (IllegalArgumentException exception) {
                display.remove();
                continue;
            }
            Camp camp = repository.get(ownerId).orElse(null);
            if (camp == null || !belongsTo(display, camp)) {
                display.remove();
                continue;
            }
            Entity tracked = camp.hologramId() == null ? null : Bukkit.getEntity(camp.hologramId());
            if (tracked instanceof TextDisplay current && current.isValid() && !current.getUniqueId().equals(display.getUniqueId())) {
                display.remove();
                continue;
            }
            if (!display.getUniqueId().equals(camp.hologramId())) {
                camp.hologramId(display.getUniqueId());
                repository.markDirty();
            }
            display.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerId.toString());
            if (legacyOwnerKey != null) display.getPersistentDataContainer().remove(legacyOwnerKey);
        }
    }

    private boolean belongsTo(TextDisplay display, Camp camp) {
        String stored = storedOwner(display);
        if (!camp.ownerId().toString().equals(stored) || !camp.worldId().equals(display.getWorld().getUID())) return false;
        Location anchor = camp.anchorLocation();
        if (anchor == null) return false;
        return (anchor.getBlockX() >> 4) == (display.getLocation().getBlockX() >> 4)
                && (anchor.getBlockZ() >> 4) == (display.getLocation().getBlockZ() >> 4)
                && Math.abs(anchor.getY() - display.getY()) <= 16.0D;
    }

    private void removeOwnedDisplays(Chunk chunk, UUID ownerId) {
        String expected = ownerId.toString();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay
                    && expected.equals(storedOwner(entity))) {
                entity.remove();
            }
        }
    }

    private void removePluginDisplays(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay
                    && storedOwner(entity) != null) {
                entity.remove();
            }
        }
    }

    private TextDisplay spawn(Camp camp) {
        Location anchor = camp.anchorLocation();
        if (anchor == null || !anchor.getWorld().isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) return null;
        double height = plugin.getConfig().getDouble("settings.hologram.height", 2.4D);
        TextDisplay display = anchor.getWorld().spawn(anchor.clone().add(0.5D, height, 0.5D), TextDisplay.class, text -> {
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setShadowed(plugin.getConfig().getBoolean("settings.hologram.shadowed", true));
            text.setSeeThrough(false);
            text.setDefaultBackground(false);
            text.setBackgroundColor(Color.fromARGB(plugin.getConfig().getInt("settings.hologram.background-argb", 1711276032)));
            text.setViewRange((float) plugin.getConfig().getDouble("settings.hologram.view-range", 32.0D));
            text.setPersistent(true);
            text.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, camp.ownerId().toString());
        });
        camp.hologramId(display.getUniqueId());
        repository.markDirty();
        return display;
    }

    private String storedOwner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return value != null || legacyOwnerKey == null ? value
                : entity.getPersistentDataContainer().get(legacyOwnerKey, PersistentDataType.STRING);
    }

    private void update(TextDisplay display, Camp camp) {
        long now = System.currentTimeMillis();
        Map<String, Object> replacements = Map.of(
                "owner", camp.ownerName(),
                "level", camps.levelName(camp.level()),
                "style", camps.styleName(camp),
                "barrier", messages.raw(camp.mobsDisabled() ? "hologram.barrier-on" : "hologram.barrier-off"),
                "time", TimeUtil.formatMillis(camp.remainingBurnMillis(now))
        );
        StringBuilder text = new StringBuilder();
        for (int line = 1; line <= 4; line++) {
            if (!text.isEmpty()) text.append('\n');
            text.append(messages.replace(messages.raw("hologram.line-" + line), replacements));
        }
        display.text(ColorUtil.component(text.toString()));
    }

    private void stopTask() {
        if (task != null) task.cancel();
        task = null;
    }
}
