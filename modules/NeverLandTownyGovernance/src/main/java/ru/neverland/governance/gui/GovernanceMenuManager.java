package ru.neverland.governance.gui;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.governance.integration.ItemsAdderHook;
import ru.neverland.governance.integration.TownyHook;
import ru.neverland.governance.model.HistoryEntry;
import ru.neverland.governance.model.LawDefinition;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.OfficeHolder;
import ru.neverland.governance.model.Proposal;
import ru.neverland.governance.model.ProposalAction;
import ru.neverland.governance.model.TownGovernanceData;
import ru.neverland.governance.model.VoteChoice;
import ru.neverland.governance.service.GovernanceService;
import ru.neverland.governance.service.MessageService;
import ru.neverland.governance.util.ColorUtil;
import ru.neverland.governance.util.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GovernanceMenuManager implements Listener {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withLocale(new Locale("ru")).withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin; private final TownyHook towny; private final GovernanceService governance;
    private final ItemsAdderHook itemsAdder; private final MessageService messages; private final NamespacedKey actionKey; private final NamespacedKey valueKey;

    public GovernanceMenuManager(JavaPlugin plugin, TownyHook towny, GovernanceService governance, ItemsAdderHook itemsAdder, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.governance = governance; this.itemsAdder = itemsAdder; this.messages = messages;
        actionKey = new NamespacedKey(plugin, "menu_action"); valueKey = new NamespacedKey(plugin, "menu_value");
    }

    public void openMain(Player player) {
        Town town = requireTown(player); if (town == null) return; TownGovernanceData data = governance.data(town);
        Inventory menu = menu(GovernanceMenuHolder.Type.MAIN, town, 45, config("gui.main-title")); fill(menu);
        menu.setItem(10, tagged(Material.BOOKSHELF, "main_laws", "", "&#63E6BEЗаконы города", List.of(
                "&7Действует: &f" + data.activeLaws().size(), "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(12, tagged(Material.CLOCK, "main_votes", "", "&#74C0FCГолосования", List.of(
                "&7Открыто: &f" + governance.open(town).size(), "", "&#FFD166Нажмите, чтобы проголосовать")));
        menu.setItem(14, tagged(Material.PLAYER_HEAD, "main_council", "", "&#C77DFFГородской совет", List.of(
                "&7Членов совета: &f" + governance.council(town).size(), "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(16, tagged(Material.COMPARATOR, "main_modifiers", "", "&#FFA94DДействующие решения", modifierLore(town)));
        menu.setItem(31, tagged(Material.WRITABLE_BOOK, "main_history", "", "&#ADB5BDИстория решений", List.of(
                "&7Записей: &f" + data.history().size(), "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(40, item(Material.MAP, "&#FFFFFFГород: &#63E6BE" + town.getName(), List.of(
                "&7Налог: &f" + number(town.getTaxes()) + (town.isTaxPercentage() ? "%" : " монет"),
                "&7Бонусных участков: &f" + town.getBonusBlocks())));
        player.openInventory(menu);
    }

    public void openLaws(Player player) {
        Town town = requireTown(player); if (town == null) return; TownGovernanceData data = governance.data(town);
        Inventory menu = menu(GovernanceMenuHolder.Type.LAWS, town, 54, config("gui.laws-title")); fill(menu);
        int slot = 0;
        for (LawDefinition law : governance.laws()) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            boolean active = data.activeLaws().containsKey(law.id()); List<String> lore = new ArrayList<>(law.description()); lore.add("");
            lore.add("&7Категория: &f" + law.category().display()); lore.add(active ? "&#63E6BE● Закон действует" : "&#ADB5BD○ Закон не принят");
            appendEffects(lore, law); lore.add("");
            if (active) lore.add("&#FF6B6BShift + ПКМ — предложить отмену"); else lore.add("&#63E6BEShift + ЛКМ — предложить принятие");
            menu.setItem(slot++, tagged(icon(law), "law", law.id(), law.name(), lore));
        }
        menu.setItem(49, back()); player.openInventory(menu);
    }

    public void openVotes(Player player) {
        Town town = requireTown(player); if (town == null) return;
        Inventory menu = menu(GovernanceMenuHolder.Type.VOTES, town, 54, config("gui.votes-title")); fill(menu);
        List<Proposal> proposals = governance.open(town); int slot = 0;
        for (Proposal proposal : proposals) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            LawDefinition law = governance.law(proposal.lawId()); if (law == null) continue;
            Map<VoteChoice, Integer> counts = proposal.counts(); VoteChoice own = proposal.voteOf(player.getUniqueId());
            List<String> lore = new ArrayList<>(List.of("&7ID: &f" + proposal.shortId(), "&7Действие: &f" + action(proposal.action()),
                    "&7Автор: &f" + proposal.proposerName(), "&7Осталось: &f" + TimeUtil.format(proposal.endsAt() - System.currentTimeMillis()), "",
                    "&#63E6BEЗа: &f" + counts.get(VoteChoice.YES), "&#FF6B6BПротив: &f" + counts.get(VoteChoice.NO),
                    "&#FFD166Воздержались: &f" + counts.get(VoteChoice.ABSTAIN), "&7Ваш голос: &f" + (own == null ? "не отдан" : governance.choiceText(own)), "",
                    "&#63E6BEЛКМ — за", "&#FF6B6BПКМ — против", "&#FFD166Shift + ЛКМ — воздержаться"));
            menu.setItem(slot++, tagged(icon(law), "proposal", proposal.shortId(), law.name(), lore));
        }
        if (proposals.isEmpty()) menu.setItem(22, item(Material.BARRIER, "&#ADB5BDНет открытых голосований", List.of("&7Совет пока не рассматривает решений.")));
        menu.setItem(49, back()); player.openInventory(menu);
    }

    public void openCouncil(Player player) {
        Town town = requireTown(player); if (town == null) return; TownGovernanceData data = governance.data(town);
        Inventory menu = menu(GovernanceMenuHolder.Type.COUNCIL, town, 54, config("gui.council-title")); fill(menu);
        int slot = 10;
        for (OfficeDefinition office : governance.offices()) {
            if (slot % 9 == 8) slot += 2; if (slot >= 45) break;
            List<OfficeHolder> holders = data.offices().getOrDefault(office.id(), List.of()); List<String> lore = new ArrayList<>(office.description()); lore.add("");
            lore.add("&7Мест: &f" + holders.size() + "/" + office.maxHolders()); lore.add("&7Член совета: &f" + (office.councilMember() ? "Да" : "Нет")); lore.add("");
            if (holders.isEmpty()) lore.add("&#ADB5BDНикто не назначен"); else holders.forEach(holder -> lore.add("&#63E6BE• &f" + holder.residentName()));
            lore.add(""); lore.add("&8Назначение: /t governance appoint");
            menu.setItem(slot++, item(icon(office), office.name(), lore));
        }
        List<String> members = governance.council(town).stream().map(towny::resident).filter(java.util.Objects::nonNull).map(value -> value.getName()).sorted().toList();
        menu.setItem(40, item(Material.BELL, "&#FFD166Состав совета", members.isEmpty() ? List.of("&7Совет не сформирован") : members.stream().map(name -> "&7• &f" + name).toList()));
        menu.setItem(49, back()); player.openInventory(menu);
    }

    public void openHistory(Player player) {
        Town town = requireTown(player); if (town == null) return; Inventory menu = menu(GovernanceMenuHolder.Type.HISTORY, town, 54, config("gui.history-title")); fill(menu);
        int slot = 0;
        for (HistoryEntry entry : governance.data(town).history()) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            menu.setItem(slot++, item(Material.PAPER, "&#FFFFFF" + entry.description(), List.of("&7" + DATE.format(Instant.ofEpochMilli(entry.timestamp())), "&7Инициатор: &f" + entry.actorName())));
        }
        menu.setItem(49, back()); player.openInventory(menu);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GovernanceMenuHolder holder)) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = clicked.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "main_laws" -> openLaws(player); case "main_votes" -> openVotes(player); case "main_council" -> openCouncil(player);
            case "main_history" -> openHistory(player); case "back" -> openMain(player);
            case "law" -> {
                if (!event.isShiftClick()) return; Town town = towny.town(player); if (town == null) return;
                boolean active = governance.data(town).activeLaws().containsKey(value); ProposalAction proposalAction = active && event.isRightClick() ? ProposalAction.REPEAL
                        : !active && event.isLeftClick() ? ProposalAction.ENACT : null;
                if (proposalAction != null) handleProposal(player, governance.propose(player, value, proposalAction));
            }
            case "proposal" -> {
                VoteChoice choice = event.isShiftClick() && event.isLeftClick() ? VoteChoice.ABSTAIN : event.isLeftClick() ? VoteChoice.YES : event.isRightClick() ? VoteChoice.NO : null;
                if (choice != null) handleVote(player, governance.vote(player, value, choice));
            }
            default -> { }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getInventory().getHolder() instanceof GovernanceMenuHolder) event.setCancelled(true); }

    public void handleProposal(Player player, GovernanceService.ProposalResult result) {
        switch (result.result()) {
            case SUCCESS -> messages.send(player, "proposal-created", Map.of("id", result.proposal().shortId(), "law", governance.law(result.proposal().lawId()).name(), "time", TimeUtil.format(result.remainingMillis())));
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "cannot-propose");
            case UNKNOWN_LAW -> messages.send(player, "unknown-law", Map.of("law", "?")); case ALREADY_ACTIVE -> messages.send(player, "already-active");
            case NOT_ACTIVE -> messages.send(player, "not-active"); case DUPLICATE -> messages.send(player, "duplicate-proposal");
            case LIMIT -> messages.send(player, "proposal-limit", Map.of("max", plugin.getConfig().getInt("voting.max-open-per-town", 5)));
            case COOLDOWN -> messages.send(player, "proposal-cooldown", Map.of("time", TimeUtil.format(result.remainingMillis())));
            default -> messages.send(player, "no-permission");
        }
    }

    public void handleVote(Player player, GovernanceService.VoteResult result) {
        switch (result.result()) {
            case SUCCESS -> { VoteChoice choice = result.proposal().voteOf(player.getUniqueId()); messages.send(player, "vote-recorded", Map.of("id", result.proposal().shortId(), "choice", governance.choiceText(choice))); }
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "no-permission");
            case UNKNOWN_PROPOSAL -> messages.send(player, "unknown-proposal", Map.of("id", "?")); case NOT_ELIGIBLE -> messages.send(player, "not-eligible");
            case CHANGE_DISABLED -> messages.send(player, "vote-change-disabled"); default -> messages.send(player, "no-permission");
        }
    }

    private Town requireTown(Player player) { Town town = towny.town(player); if (town == null) messages.send(player, "no-town"); return town; }
    private Inventory menu(GovernanceMenuHolder.Type type, Town town, int size, String title) { return Bukkit.createInventory(new GovernanceMenuHolder(type, town.getUUID()), size, ColorUtil.color(title)); }
    private String config(String path) { return plugin.getConfig().getString(path, path); }
    private void fill(Inventory menu) { Material material = Material.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")); ItemStack filler = item(material == null ? Material.BLACK_STAINED_GLASS_PANE : material, " ", List.of()); for (int slot = 0; slot < menu.getSize(); slot++) menu.setItem(slot, filler); }
    private ItemStack back() { return tagged(Material.ARROW, "back", "", "&#ADB5BDНазад", List.of("&7Вернуться в главное меню")); }
    private ItemStack icon(LawDefinition law) { ItemStack custom = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(law.itemsAdderIcon()) : null; return custom == null ? new ItemStack(law.material()) : custom; }
    private ItemStack icon(OfficeDefinition office) { ItemStack custom = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(office.itemsAdderIcon()) : null; return custom == null ? new ItemStack(office.material()) : custom; }
    private ItemStack item(Material material, String name, List<String> lore) { return item(new ItemStack(material), name, lore); }
    @SuppressWarnings("deprecation") private ItemStack item(ItemStack stack, String name, List<String> lore) { ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ColorUtil.color(name)); meta.setLore(lore.stream().map(ColorUtil::color).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); stack.setItemMeta(meta); return stack; }
    private ItemStack tagged(Material material, String action, String value, String name, List<String> lore) { return tagged(new ItemStack(material), action, value, name, lore); }
    private ItemStack tagged(ItemStack stack, String action, String value, String name, List<String> lore) { stack = item(stack, name, lore); ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value == null ? "" : value); stack.setItemMeta(meta); return stack; }
    private void appendEffects(List<String> lore, LawDefinition law) { var effects = law.effects(); if (effects.changesTax()) lore.add("&7Налог: &f" + number(effects.taxAmount()) + (effects.taxPercentage() ? "%" : " монет")); if (effects.constructionCostMultiplier() != 1) lore.add("&7Стоимость строительства: &f" + percent(effects.constructionCostMultiplier())); if (effects.ideologyCostMultiplier() != 1) lore.add("&7Стоимость идеологии: &f" + percent(effects.ideologyCostMultiplier())); if (effects.ideologyExperienceMultiplier() != 1) lore.add("&7Опыт идеологии: &f" + percent(effects.ideologyExperienceMultiplier())); if (effects.bonusClaimBlocks() != 0) lore.add("&7Дополнительные участки: &f+" + effects.bonusClaimBlocks()); }
    private List<String> modifierLore(Town town) { return List.of("&7Стоимость строительства: &f" + percent(governance.constructionCost(town.getUUID())), "&7Стоимость идеологии: &f" + percent(governance.ideologyCost(town.getUUID())), "&7Опыт идеологии: &f" + percent(governance.ideologyExperience(town.getUUID())), "", "&7Множители доступны другим аддонам через API."); }
    private String percent(double multiplier) { return Math.round(multiplier * 100) + "%"; }
    private String number(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value); }
    private boolean isBorder(int slot) { int column = slot % 9; return column == 0 || column == 8; }
    private String action(ProposalAction action) { return action == ProposalAction.ENACT ? "Принятие" : "Отмена"; }
}
