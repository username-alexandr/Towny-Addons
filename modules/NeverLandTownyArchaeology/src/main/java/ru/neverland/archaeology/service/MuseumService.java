package ru.neverland.archaeology.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.api.MuseumSnapshot;
import ru.neverland.archaeology.event.ArtifactDonateEvent;
import ru.neverland.archaeology.event.CollectionCompleteEvent;
import ru.neverland.archaeology.event.WonderArtifactsConsumeEvent;
import ru.neverland.archaeology.integration.TownyHook;
import ru.neverland.archaeology.model.ArtifactDefinition;
import ru.neverland.archaeology.model.ArtifactIdentity;
import ru.neverland.archaeology.model.CollectionDefinition;
import ru.neverland.archaeology.model.TownMuseumData;
import ru.neverland.archaeology.util.ArtifactBatchMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MuseumService {
    public enum DonateStatus { SUCCESS, NO_TOWN, NOT_ARTIFACT, FORGED, DUPLICATE, CANCELLED, NOTHING }
    public record DonateResult(DonateStatus status, int count, int points, String artifactId, Town town) { }
    private final JavaPlugin plugin; private final TownyHook towny; private final ArchaeologyRegistry registry; private final ArchaeologyRepository repository; private final ArtifactService artifacts; private final MessageService messages;
    public MuseumService(JavaPlugin plugin, TownyHook towny, ArchaeologyRegistry registry, ArchaeologyRepository repository, ArtifactService artifacts, MessageService messages) { this.plugin = plugin; this.towny = towny; this.registry = registry; this.repository = repository; this.artifacts = artifacts; this.messages = messages; }
    public DonateResult donateHand(Player player) { Town town = towny.town(player); if (town == null) return new DonateResult(DonateStatus.NO_TOWN, 0, 0, null, null); ItemStack stack = player.getInventory().getItemInMainHand(); return donateStack(player, town, stack, true); }
    public DonateResult donateAll(Player player) { Town town = towny.town(player); if (town == null) return new DonateResult(DonateStatus.NO_TOWN, 0, 0, null, null); int count = 0; int points = 0; ItemStack[] contents = player.getInventory().getStorageContents(); for (int slot = 0; slot < contents.length; slot++) { ItemStack stack = contents[slot]; DonateResult result = donateStack(player, town, stack, false); if (result.status() == DonateStatus.SUCCESS) { count += result.count(); points += result.points(); contents[slot] = null; } } player.getInventory().setStorageContents(contents); return count == 0 ? new DonateResult(DonateStatus.NOTHING, 0, 0, null, town) : new DonateResult(DonateStatus.SUCCESS, count, points, null, town); }
    private DonateResult donateStack(Player player, Town town, ItemStack stack, boolean consume) { ArtifactIdentity identity = artifacts.identity(stack); if (identity == null) return new DonateResult(DonateStatus.NOT_ARTIFACT, 0, 0, null, town); if (!identity.valid()) return new DonateResult(DonateStatus.FORGED, 0, 0, identity.artifactId(), town); String serial = identity.serial().toString(); int amount = ArtifactBatchMath.acceptedNow(identity.batchSize(), repository.acceptedCount(serial), stack.getAmount()); if (amount <= 0) return new DonateResult(DonateStatus.DUPLICATE, 0, 0, identity.artifactId(), town); ArtifactDonateEvent event = new ArtifactDonateEvent(player, town.getUUID(), identity.artifactId(), amount); Bukkit.getPluginManager().callEvent(event); if (event.isCancelled()) return new DonateResult(DonateStatus.CANCELLED, 0, 0, identity.artifactId(), town); ArtifactDefinition definition = registry.artifact(identity.artifactId()); TownMuseumData museum = repository.museum(town.getUUID(), town.getName()); museum.add(definition.id(), amount, definition.points(), player.getUniqueId()); repository.accept(serial, amount); checkCollections(town, museum); repository.dirty(); if (consume) { if (stack.getAmount() <= amount) player.getInventory().setItemInMainHand(null); else stack.setAmount(stack.getAmount() - amount); } return new DonateResult(DonateStatus.SUCCESS, amount, definition.points() * amount, definition.id(), town); }
    private void checkCollections(Town town, TownMuseumData museum) { for (CollectionDefinition collection : registry.collections()) { if (museum.completedCollections().contains(collection.id())) continue; boolean complete = collection.artifacts().stream().allMatch(id -> museum.donated(id) > 0); if (!complete) continue; museum.completedCollections().add(collection.id()); double money = Math.max(0, plugin.getConfig().getDouble("museum.collection-reward-money", 10000)); if (money > 0) town.getAccount().deposit(money, "Завершение музейной коллекции"); int blocks = Math.max(0, plugin.getConfig().getInt("museum.collection-reward-bonus-blocks", 3)); if (blocks > 0) { town.setBonusBlocks(town.getBonusBlocks() + blocks); town.save(); } Bukkit.getPluginManager().callEvent(new CollectionCompleteEvent(town.getUUID(), collection.id())); if (plugin.getConfig().getBoolean("museum.announce-completion", true)) { String text = messages.text("collection-complete", Map.of("town", town.getName(), "collection", collection.name()), true); Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(text)); Bukkit.getConsoleSender().sendMessage(text); } } }
    public MuseumSnapshot snapshot(UUID townId) { TownMuseumData data = repository.museum(townId); if (data == null) { Town town = towny.town(townId); return new MuseumSnapshot(townId, town == null ? townId.toString() : town.getName(), 0, Map.of(), Map.of(), java.util.Set.of()); } return new MuseumSnapshot(townId, data.townName(), data.points(), Map.copyOf(data.available()), Map.copyOf(data.donated()), java.util.Set.copyOf(data.completedCollections())); }
    public Map<String, Integer> missing(UUID townId, String wonderId) { Map<String, Integer> requirements = registry.wonderRequirements(wonderId); TownMuseumData museum = repository.museum(townId); Map<String, Integer> missing = new LinkedHashMap<>(); for (Map.Entry<String, Integer> entry : requirements.entrySet()) { int has = museum == null ? 0 : museum.available(entry.getKey()); if (has < entry.getValue()) missing.put(entry.getKey(), entry.getValue() - has); } return Map.copyOf(missing); }
    public boolean consumeForWonder(UUID townId, String wonderId, UUID actorId) { Map<String, Integer> requirements = registry.wonderRequirements(wonderId); if (requirements.isEmpty() || !missing(townId, wonderId).isEmpty()) return requirements.isEmpty(); WonderArtifactsConsumeEvent event = new WonderArtifactsConsumeEvent(townId, wonderId, actorId, requirements); Bukkit.getPluginManager().callEvent(event); if (event.isCancelled()) return false; TownMuseumData museum = repository.museum(townId); boolean consumed = museum != null && museum.consume(requirements); if (consumed) repository.dirty(); return consumed; }
    public void add(UUID townId, String townName, String artifactId, int amount) { ArtifactDefinition definition = registry.artifact(artifactId); if (definition == null || amount <= 0) return; TownMuseumData museum = repository.museum(townId, townName); museum.add(definition.id(), amount, definition.points(), null); Town town = towny.town(townId); if (town != null) checkCollections(town, museum); repository.dirty(); }
    public int available(UUID townId, String artifactId) { TownMuseumData data = repository.museum(townId); return data == null ? 0 : data.available(artifactId); }
    public String formatMissing(Map<String, Integer> missing) { List<String> values = new ArrayList<>(); missing.forEach((id, amount) -> values.add(artifacts.displayName(id) + " x" + amount)); return String.join(", ", values); }
}
