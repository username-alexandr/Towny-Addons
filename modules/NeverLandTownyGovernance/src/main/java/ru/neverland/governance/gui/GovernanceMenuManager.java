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
    private final CouncilOfficeMenus officeMenus;
    private final JavaPlugin plugin; private final TownyHook towny; private final GovernanceService governance;
    private final ItemsAdderHook itemsAdder; private final MessageService messages; private final NamespacedKey actionKey; private final NamespacedKey valueKey;

    public GovernanceMenuManager(JavaPlugin plugin, TownyHook towny, GovernanceService governance, ItemsAdderHook itemsAdder, MessageService messages) {
        this.plugin = plugin; this.towny = towny; this.governance = governance; this.itemsAdder = itemsAdder; this.messages = messages;
        officeMenus = new CouncilOfficeMenus(plugin, towny, governance, itemsAdder, messages, this::openMain);
        actionKey = new NamespacedKey(plugin, "menu_action"); valueKey = new NamespacedKey(plugin, "menu_value");
    }

    public void openMain(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        Town town = requireTown(player); if (town == null) return; TownGovernanceData data = governance.data(town);
        Inventory menu = menu(player, GovernanceMenuHolder.Type.MAIN, town, 45, config("gui.main-title")); fill(menu);
        menu.setItem(10, tagged(Material.BOOKSHELF, "main_laws", "", "&#63E6BEЗаконы города", List.of(
                "&7Действует: &f" + data.activeLaws().size(), "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(12, tagged(Material.CLOCK, "main_votes", "", "&#74C0FCГолосования", List.of(
                "&7Открыто: &f" + governance.open(town).size(), "", "&#FFD166Нажмите, чтобы проголосовать")));
        menu.setItem(14, tagged(Material.PLAYER_HEAD, "main_council", "", "&#C77DFFДолжности и права", List.of(
                "&7Назначения в совет, министры и права жителей", "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(16, tagged(Material.COMPARATOR, "main_modifiers", "", "&#FFA94DДействующие решения", modifierLore(town)));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyPolicies"))menu.setItem(29,tagged(Material.PAPER,"main_policies","","&eГородские указы",List.of("&7Экономические политики мэра и их издержки.")));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyTreasuryPlus"))menu.setItem(33,tagged(Material.GOLD_INGOT,"main_treasury","","&eКазна и бюджет",List.of("&7Статьи расходов и недельные отчёты.")));
        menu.setItem(31, tagged(Material.WRITABLE_BOOK, "main_history", "", "&#ADB5BDИстория решений", List.of(
                "&7Записей: &f" + data.history().size(), "", "&#FFD166Нажмите, чтобы открыть")));
        menu.setItem(40, item(Material.MAP, "&#FFFFFFГород: &#63E6BE" + town.getName(), List.of(
                "&7Налог: &f" + number(town.getTaxes()) + (town.isTaxPercentage() ? "%" : " монет"),
                "&7Бонусных участков: &f" + town.getBonusBlocks())));
        player.openInventory(menu);
    } }

    public void openLaws(Player player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Town town = requireTown(player); if (town == null) return; TownGovernanceData data = governance.data(town);
        Inventory menu = menu(player, GovernanceMenuHolder.Type.LAWS, town, 54, config("gui.laws-title")); fill(menu);
        int slot = 0;
        for (LawDefinition law : governance.laws()) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            boolean active = data.activeLaws().containsKey(law.id()); List<String> lore = new ArrayList<>(law.description()); lore.add("");
            lore.add("&7Категория: &f" + law.category().display()); lore.add(active ? "&#63E6BE● Закон действует" : "&#ADB5BD○ Закон не принят");
            appendEffects(lore, law); lore.add("");
            lore.add("&eОткрыть условия и действия");
            menu.setItem(slot++, tagged(icon(law), "law", law.id(), law.name(), lore));
        }
        menu.setItem(49, back()); player.openInventory(menu);
    } }

    public void openVotes(Player player) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        Town town = requireTown(player); if (town == null) return;
        Inventory menu = menu(player, GovernanceMenuHolder.Type.VOTES, town, 54, config("gui.votes-title")); fill(menu);
        List<Proposal> proposals = governance.open(town); int slot = 0;
        for (Proposal proposal : proposals) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            LawDefinition law = governance.law(proposal.lawId()); if (law == null) continue;
            Map<VoteChoice, Integer> counts = proposal.counts(); VoteChoice own = proposal.voteOf(player.getUniqueId());
            List<String> lore = new ArrayList<>(List.of("&7Действие: &f" + action(proposal.action()),
                    "&7Автор: &f" + proposal.proposerName(), "&7Осталось: &f" + (proposal.timer().paused()?"Пауза • ":"")+TimeUtil.format(proposal.timer().remaining(System.currentTimeMillis())), "",
                    "&#63E6BEЗа: &f" + counts.get(VoteChoice.YES), "&#FF6B6BПротив: &f" + counts.get(VoteChoice.NO),
                    "&#FFD166Воздержались: &f" + counts.get(VoteChoice.ABSTAIN), "&7Ваш голос: &f" + (own == null ? "не отдан" : governance.choiceText(own)), "",
                    "&eОткрыть голосование и выбрать ответ"));
            menu.setItem(slot++, tagged(icon(law), "proposal", proposal.shortId(), law.name(), lore));
        }
        if (proposals.isEmpty()) menu.setItem(22, item(Material.BARRIER, "&#ADB5BDНет открытых голосований", List.of("&7Совет пока не рассматривает решений.")));
        menu.setItem(49, back()); player.openInventory(menu);
    } }

    public void openCouncil(Player player) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        officeMenus.openCouncil(player, 0);
    } }

    public void openHistory(Player player) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        Town town = requireTown(player); if (town == null) return; Inventory menu = menu(player, GovernanceMenuHolder.Type.HISTORY, town, 54, config("gui.history-title")); fill(menu);
        int slot = 0;
        for (HistoryEntry entry : governance.data(town).history()) {
            while (slot < 45 && isBorder(slot)) slot++; if (slot >= 45) break;
            menu.setItem(slot++, item(Material.PAPER, "&#FFFFFF" + entry.description(), List.of("&7" + DATE.format(Instant.ofEpochMilli(entry.timestamp())), "&7Инициатор: &f" + entry.actorName())));
        }
        menu.setItem(49, back()); player.openInventory(menu);
    } }

    @EventHandler public void onClick(InventoryClickEvent event) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyGovernance")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (!(event.getInventory().getHolder() instanceof GovernanceMenuHolder holder)) return; event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if(holder.viewer()==null||!player.getUniqueId().equals(holder.viewer())||event.getClick()!=org.bukkit.event.inventory.ClickType.LEFT)return;
        if (officeMenus.handles(holder)||holder.type()==GovernanceMenuHolder.Type.LAW_DETAIL||holder.type()==GovernanceMenuHolder.Type.VOTE_DETAIL) { officeMenus.click(event, player, holder); return; }
        ItemStack clicked = event.getCurrentItem(); if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = clicked.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);
        if (action == null) return;
        holder.action(event.getRawSlot(),who->navigate(who,action,value));
        officeMenus.click(event,player,holder);
    } }
    private void navigate(Player player,String action,String value){
        switch(action){
            case "main_laws" -> openLaws(player);case "main_votes" -> openVotes(player);case "main_council" -> openCouncil(player);
            case "main_treasury" -> player.performCommand("townytreasury");case "main_policies" -> player.performCommand("townypolicies");
            case "main_history" -> openHistory(player);case "back" -> openMain(player);
            case "law" -> openLaw(player,value);case "proposal" -> openVote(player,value);default -> { }
        }
    }
    private void actionButton(Inventory menu,int slot,Material material,String title,List<String> lore,java.util.function.Consumer<Player> action){
        menu.setItem(slot,item(material,title,lore));if(action!=null)((GovernanceMenuHolder)menu.getHolder()).action(slot,action);
    }
    public void openLaw(Player player,String lawId){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        Town town=requireTown(player);if(town==null)return;LawDefinition law=governance.law(lawId);if(law==null){openLaws(player);return;}
        boolean active=governance.data(town).activeLaws().containsKey(lawId);ProposalAction action=active?ProposalAction.REPEAL:ProposalAction.ENACT;
        Inventory menu=menu(player,GovernanceMenuHolder.Type.LAW_DETAIL,town,54,"Закон • "+ColorUtil.strip(law.name()));fill(menu);
        List<String> lore=new ArrayList<>(law.description());appendEffects(lore,law);lore.add(active?"&aЗакон уже действует":"&7Закон ещё не принят");
        lore.add("&7Изменение вступит в силу после принятия на голосовании.");
        if(governance.electorate(town).contains(player.getUniqueId()))lore.add("&7При предложении ваш голос «за» учитывается сразу.");
        menu.setItem(13,item(icon(law),law.name(),lore));
        Proposal existing=governance.open(town).stream().filter(v->v.lawId().equals(lawId)).findFirst().orElse(null);
        String problem=governance.proposalAccessReason(player,town,law.category());long created=System.nanoTime();
        if(existing!=null)actionButton(menu,29,Material.CLOCK,"&eОткрыть голосование",List.of("&7Этот закон уже обсуждается."),p->openVote(p,existing.id().toString()));
        else actionButton(menu,29,problem.isEmpty()?Material.LIME_DYE:Material.GRAY_DYE,active?"&eПредложить отмену закона":"&aПредложить закон",List.of(problem.isEmpty()?"&7Начать голосование по этому решению.":"&c"+problem),problem.isEmpty()?p->{
            Town fresh=requireTown(p);if(fresh==null)return;
            if(System.nanoTime()-created>60_000_000_000L||!law.equals(governance.law(lawId))||active!=governance.data(fresh).activeLaws().containsKey(lawId)){
                p.sendMessage("§eУсловия изменились или карточка устарела. Проверьте закон ещё раз.");openLaw(p,lawId);return;
            }
            var result=governance.propose(p,lawId,action);handleProposal(p,result);
            if(result.result()==GovernanceService.Result.SUCCESS)openVote(p,result.proposal().id().toString());else openLaw(p,lawId);
        }:null);
        actionButton(menu,49,Material.ARROW,"&7Назад",List.of("&7К законам города"),this::openLaws);player.openInventory(menu);
    } }
    public void openVote(Player player,String proposalId){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        Town town=requireTown(player);if(town==null)return;Proposal proposal=governance.find(town,proposalId);
        if(proposal==null){player.sendMessage("§eГолосование уже завершено.");openVotes(player);return;}
        LawDefinition law=governance.law(proposal.lawId());if(law==null){openVotes(player);return;}
        Inventory menu=menu(player,GovernanceMenuHolder.Type.VOTE_DETAIL,town,54,"Голосование • "+ColorUtil.strip(law.name()));fill(menu);
        List<String> lore=new ArrayList<>(law.description());appendEffects(lore,law);
        lore.add("&7Решение: &f"+action(proposal.action()));lore.add("&7Осталось: &f"+(proposal.timer().paused()?"Пауза":TimeUtil.format(proposal.timer().remaining(System.currentTimeMillis()))));
        lore.add("&7Ваш голос: &f"+(proposal.voteOf(player.getUniqueId())==null?"не отдан":governance.choiceText(proposal.voteOf(player.getUniqueId()))));
        var counts=proposal.counts();for(VoteChoice choice:VoteChoice.values())lore.add("&7"+governance.choiceText(choice)+": &f"+counts.get(choice));
        menu.setItem(13,item(icon(law),law.name(),lore));String problem=governance.voteAccessReason(player,town,proposal);
        VoteChoice[] choices={VoteChoice.YES,VoteChoice.NO,VoteChoice.ABSTAIN};Material[] icons={Material.LIME_DYE,Material.RED_DYE,Material.YELLOW_DYE};
        String[] labels={"&aЗа","&cПротив","&eВоздержаться"};
        for(int i=0;i<choices.length;i++){
            VoteChoice choice=choices[i];actionButton(menu,29+i*2,problem.isEmpty()?icons[i]:Material.GRAY_DYE,labels[i],List.of(problem.isEmpty()?"&7Записать этот ответ.":"&c"+problem),problem.isEmpty()?p->{
                var result=governance.vote(p,proposal.id().toString(),choice);handleVote(p,result);openVote(p,proposal.id().toString());
            }:null);
        }
        actionButton(menu,49,Material.ARROW,"&7Назад",List.of("&7К списку голосований"),this::openVotes);player.openInventory(menu);
    } }
    @EventHandler public void onDrag(InventoryDragEvent event) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyGovernance")){ru.neverland.core.ModuleGate.stopEvent(event);return;} if (event.getInventory().getHolder() instanceof GovernanceMenuHolder) event.setCancelled(true); } }

    public void handleProposal(Player player, GovernanceService.ProposalResult result) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        switch (result.result()) {
            case SUCCESS -> messages.send(player, "proposal-created", Map.of("id", result.proposal().shortId(), "law", governance.law(result.proposal().lawId()).name(), "time", TimeUtil.format(result.remainingMillis())));
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "cannot-propose");
            case UNKNOWN_LAW -> messages.send(player, "unknown-law", Map.of("law", "?")); case ALREADY_ACTIVE -> messages.send(player, "already-active");
            case NOT_ACTIVE -> messages.send(player, "not-active"); case DUPLICATE -> messages.send(player, "duplicate-proposal");
            case LIMIT -> messages.send(player, "proposal-limit", Map.of("max", plugin.getConfig().getInt("voting.max-open-per-town", 5)));
            case COOLDOWN -> messages.send(player, "proposal-cooldown", Map.of("time", TimeUtil.format(result.remainingMillis())));
            default -> messages.send(player, "no-permission");
        }
    } }

    public void handleVote(Player player, GovernanceService.VoteResult result) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        switch (result.result()) {
            case SUCCESS -> { VoteChoice choice = result.proposal().voteOf(player.getUniqueId()); messages.send(player, "vote-recorded", Map.of("id", result.proposal().shortId(), "choice", governance.choiceText(choice))); }
            case NO_TOWN -> messages.send(player, "no-town"); case NO_PERMISSION -> messages.send(player, "no-permission");
            case PAUSED -> player.sendMessage("§eГолосование приостановлено. Сейчас изменить голос нельзя.");
            case UNKNOWN_PROPOSAL -> messages.send(player, "unknown-proposal", Map.of("id", "?")); case NOT_ELIGIBLE -> messages.send(player, "not-eligible");
            case CHANGE_DISABLED -> messages.send(player, "vote-change-disabled"); default -> messages.send(player, "no-permission");
        }
    } }

    private Town requireTown(Player player) { Town town = towny.town(player); if (town == null) messages.send(player, "no-town"); return town; }
    private Inventory menu(Player player, GovernanceMenuHolder.Type type, Town town, int size, String title) { return ru.neverland.core.MenuStyle.inventory(plugin, new GovernanceMenuHolder(type, town.getUUID(),player.getUniqueId()), size, ColorUtil.color(title)); }
    private String config(String path) { return plugin.getConfig().getString(path, path); }
    private void fill(Inventory menu) { Material material = Material.matchMaterial(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE")); ItemStack filler = item(material == null ? Material.BLACK_STAINED_GLASS_PANE : material, " ", List.of()); for (int slot = 0; slot < menu.getSize(); slot++) menu.setItem(slot, filler); }
    private ItemStack back() { return tagged(Material.ARROW, "back", "", "&#ADB5BDНазад", List.of("&7Вернуться в главное меню")); }
    private ItemStack icon(LawDefinition law) { ItemStack custom = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(law.itemsAdderIcon()) : null; return custom == null ? new ItemStack(law.material()) : custom; }
    private ItemStack icon(OfficeDefinition office) { ItemStack custom = plugin.getConfig().getBoolean("itemsadder.enabled", true) ? itemsAdder.item(office.itemsAdderIcon()) : null; return custom == null ? new ItemStack(office.material()) : custom; }
    private ItemStack item(Material material, String name, List<String> lore) { return item(new ItemStack(material), name, lore); }
    @SuppressWarnings("deprecation") private ItemStack item(ItemStack stack, String name, List<String> lore) { ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(ru.neverland.core.MenuStyle.nameLegacy(ColorUtil.color(name))); meta.setLore(ru.neverland.core.MenuStyle.loreStrings(lore.stream().map(ColorUtil::color).toList())); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); stack.setItemMeta(meta); return stack; }
    private ItemStack tagged(Material material, String action, String value, String name, List<String> lore) { return tagged(new ItemStack(material), action, value, name, lore); }
    private ItemStack tagged(ItemStack stack, String action, String value, String name, List<String> lore) { stack = item(stack, name, lore); ItemMeta meta = stack.getItemMeta(); meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value == null ? "" : value); stack.setItemMeta(meta); return stack; }
    private void appendEffects(List<String> lore, LawDefinition law) { var effects = law.effects(); if (effects.changesTax()) lore.add("&7Налог: &f" + number(effects.taxAmount()) + (effects.taxPercentage() ? "%" : " монет")); if (effects.constructionCostMultiplier() != 1) lore.add("&7Стоимость строительства: &f" + percent(effects.constructionCostMultiplier())); if (effects.ideologyCostMultiplier() != 1) lore.add("&7Стоимость идеологии: &f" + percent(effects.ideologyCostMultiplier())); if (effects.ideologyExperienceMultiplier() != 1) lore.add("&7Опыт идеологии: &f" + percent(effects.ideologyExperienceMultiplier())); if (effects.bonusClaimBlocks() != 0) lore.add("&7Дополнительные участки: &f+" + effects.bonusClaimBlocks()); }
    private List<String> modifierLore(Town town) { return List.of("&7Стоимость строительства: &f" + percent(governance.constructionCost(town.getUUID())), "&7Стоимость идеологии: &f" + percent(governance.ideologyCost(town.getUUID())), "&7Опыт идеологии: &f" + percent(governance.ideologyExperience(town.getUUID())), "", "&7Бонусы действующих городских законов."); }
    private String percent(double multiplier) { return Math.round(multiplier * 100) + "%"; }
    private String number(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.2f", value); }
    private boolean isBorder(int slot) { int column = slot % 9; return column == 0 || column == 8; }
    private String action(ProposalAction action) { return action == ProposalAction.ENACT ? "Принятие" : "Отмена"; }
}
