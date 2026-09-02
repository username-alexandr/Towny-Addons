package ru.neverland.archaeology.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.event.ArtifactDiscoverEvent;
import ru.neverland.archaeology.integration.ItemsAdderHook;
import ru.neverland.archaeology.model.ArtifactDefinition;
import ru.neverland.archaeology.model.ArtifactIdentity;
import ru.neverland.archaeology.model.PlayerJournal;
import ru.neverland.archaeology.model.RarityDefinition;
import ru.neverland.archaeology.util.ColorUtil;
import ru.neverland.archaeology.util.CryptoUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArtifactService implements Listener {
    private final JavaPlugin plugin; private final ArchaeologyRegistry registry; private final ArchaeologyRepository repository; private final ItemsAdderHook itemsAdder; private final MessageService messages;
    private final NamespacedKey artifactKey; private final NamespacedKey serialKey; private final NamespacedKey signatureKey; private final NamespacedKey batchSizeKey;
    private final NamespacedKey legacyArtifactKey; private final NamespacedKey legacySerialKey; private final NamespacedKey legacySignatureKey; private final NamespacedKey legacyBatchSizeKey; private final String secret;
    public ArtifactService(JavaPlugin plugin, ArchaeologyRegistry registry, ArchaeologyRepository repository, ItemsAdderHook itemsAdder, MessageService messages) {
        this.plugin = plugin; this.registry = registry; this.repository = repository; this.itemsAdder = itemsAdder; this.messages = messages;
        artifactKey = new NamespacedKey(plugin, "artifact_id"); serialKey = new NamespacedKey(plugin, "artifact_serial"); signatureKey = new NamespacedKey(plugin, "artifact_signature"); batchSizeKey = new NamespacedKey(plugin, "artifact_batch_size");
        legacyArtifactKey = NamespacedKey.fromString("townyarchaeology:artifact_id"); legacySerialKey = NamespacedKey.fromString("townyarchaeology:artifact_serial"); legacySignatureKey = NamespacedKey.fromString("townyarchaeology:artifact_signature"); legacyBatchSizeKey = NamespacedKey.fromString("townyarchaeology:artifact_batch_size"); secret = loadSecret();
    }
    public ItemStack create(String artifactId) {
        List<ItemStack> batch = createBatch(artifactId, 1);
        return batch.isEmpty() ? null : batch.get(0);
    }
    public List<ItemStack> createBatch(String artifactId, int amount) {
        ArtifactDefinition definition = registry.artifact(artifactId);
        if (definition == null || amount < 1) return List.of();
        int batchSize = amount;
        UUID serial = UUID.randomUUID();
        ItemStack prototype = createPrototype(definition, serial, batchSize);
        int maximum = Math.max(1, prototype.getMaxStackSize());
        List<ItemStack> result = new ArrayList<>();
        int remaining = batchSize;
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            stack.setAmount(Math.min(maximum, remaining));
            result.add(stack);
            remaining -= stack.getAmount();
        }
        return List.copyOf(result);
    }
    private ItemStack createPrototype(ArtifactDefinition definition, UUID serial, int batchSize) {
        ItemStack stack = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(definition.itemsAdderIcon()) : null;
        if (stack == null) stack = new ItemStack(definition.material());
        stack.setAmount(1);
        String signature = signature(definition.id(), serial, batchSize);
        ItemMeta meta = stack.getItemMeta();
        RarityDefinition rarity = registry.rarity(definition.rarity());
        meta.setDisplayName(ColorUtil.color(definition.name()));
        List<String> lore = new ArrayList<>();
        definition.lore().forEach(line -> lore.add(ColorUtil.color(line)));
        lore.add("");
        lore.add(ColorUtil.color("&7Редкость: " + rarity.name()));
        lore.add(ColorUtil.color("&7Музейная ценность: &f" + definition.points()));
        lore.add(ColorUtil.color("&8Серийный номер: " + serial.toString().substring(0, 8)));
        if (batchSize > 1) lore.add(ColorUtil.color("&8Экземпляров в партии: " + batchSize));
        lore.add("");
        lore.add(ColorUtil.color("&#63E6BEПодлинный артефакт NeverLand"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(serialKey, PersistentDataType.STRING, serial.toString());
        meta.getPersistentDataContainer().set(batchSizeKey, PersistentDataType.INTEGER, batchSize);
        meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, signature);
        stack.setItemMeta(meta);
        return stack;
    }
    public ArtifactIdentity identity(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null; ItemMeta meta = stack.getItemMeta(); String id = value(meta, artifactKey, legacyArtifactKey); String serialRaw = value(meta, serialKey, legacySerialKey); String storedSignature = value(meta, signatureKey, legacySignatureKey); if (id == null || serialRaw == null || storedSignature == null) return null;
        try {
            UUID serial = UUID.fromString(serialRaw);
            Integer storedBatchSize = integerValue(meta, batchSizeKey, legacyBatchSizeKey);
            int batchSize = storedBatchSize == null ? 1 : Math.max(1, storedBatchSize);
            boolean signedBatch = CryptoUtil.equals(storedSignature, signature(id, serial, batchSize));
            boolean signedLegacyItem = storedBatchSize == null && CryptoUtil.equals(storedSignature, legacySignature(id, serial));
            boolean valid = registry.artifact(id) != null && (signedBatch || signedLegacyItem);
            return new ArtifactIdentity(id, serial, batchSize, valid);
        } catch (IllegalArgumentException exception) {
            return new ArtifactIdentity(id, new UUID(0, 0), 1, false);
        }
    }
    public boolean authentic(ItemStack stack) { ArtifactIdentity identity = identity(stack); return identity != null && identity.valid(); }
    private String value(ItemMeta meta, NamespacedKey current, NamespacedKey legacy) { String value = meta.getPersistentDataContainer().get(current, PersistentDataType.STRING); return value != null || legacy == null ? value : meta.getPersistentDataContainer().get(legacy, PersistentDataType.STRING); }
    private Integer integerValue(ItemMeta meta, NamespacedKey current, NamespacedKey legacy) { Integer value = meta.getPersistentDataContainer().get(current, PersistentDataType.INTEGER); return value != null || legacy == null ? value : meta.getPersistentDataContainer().get(legacy, PersistentDataType.INTEGER); }
    public String displayName(String artifactId) { ArtifactDefinition definition = registry.artifact(artifactId); return definition == null ? artifactId : definition.name(); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (!(event.getEntity() instanceof Player player)) return; ArtifactIdentity identity = identity(event.getItem().getItemStack()); if (identity == null || !identity.valid()) return; PlayerJournal journal = repository.journal(player.getUniqueId(), player.getName()); String serial = identity.serial().toString(); if (journal.serials().contains(serial)) return; boolean first = journal.discover(identity.artifactId(), serial); repository.dirty(); plugin.getServer().getPluginManager().callEvent(new ArtifactDiscoverEvent(player, identity.artifactId(), first)); if (first) { ArtifactDefinition definition = registry.artifact(identity.artifactId()); messages.send(player, "artifact-found", Map.of("artifact", definition.name(), "rarity", registry.rarity(definition.rarity()).name())); } }
    private String signature(String id, UUID serial, int batchSize) { return CryptoUtil.sign(secret, id + "|" + serial + "|" + batchSize); }
    private String legacySignature(String id, UUID serial) { return CryptoUtil.sign(secret, id + "|" + serial); }
    private String loadSecret() { File file = new File(plugin.getDataFolder(), "secret.key"); try { if (file.exists()) { String value = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim(); if (!value.isBlank()) return value; } String generated = CryptoUtil.secret(); Files.writeString(file.toPath(), generated, StandardCharsets.UTF_8); return generated; } catch (IOException exception) { throw new IllegalStateException("Не удалось создать ключ подписи артефактов", exception); } }
}
