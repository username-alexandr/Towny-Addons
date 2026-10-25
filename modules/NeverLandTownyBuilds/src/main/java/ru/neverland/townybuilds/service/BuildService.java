package ru.neverland.townybuilds.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.ResourceFund;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.integration.ArchaeologyBridge;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.util.ColorUtil;
import ru.neverland.townybuilds.construction.ConstructionPreparation;
import ru.neverland.townybuilds.construction.ConstructionProgress;
import ru.neverland.townybuilds.construction.ConstructionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class BuildService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final MessageService messages;
    private final ArchaeologyBridge archaeology;
    private final RussianItemNames itemNames;
    private final DefinitionRegistry definitions;
    private ConstructionService construction;
    private ru.neverland.townybuilds.construction.PlacementService placements;
    public void placements(ru.neverland.townybuilds.construction.PlacementService value){placements=value;}

    private ru.neverland.townybuilds.construction.ConstructionRefunds refunds;
    public void refunds(ru.neverland.townybuilds.construction.ConstructionRefunds value) { refunds=value; }

    public BuildService(JavaPlugin plugin, TownyHook towny, DataStore dataStore, MessageService messages,
                        ArchaeologyBridge archaeology, RussianItemNames itemNames,
                        DefinitionRegistry definitions) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.messages = messages;
        this.archaeology = archaeology;
        this.itemNames = itemNames;
        this.definitions = definitions;
    }

    public ru.neverland.townybuilds.construction.BlueprintPlan previewPlan(String project,int level,ru.neverland.townybuilds.construction.ConstructionSite site){return construction==null?null:construction.previewPlan(project,level,site);}
    public void setConstruction(ConstructionService construction) { synchronized (ru.neverland.core.SuiteState.lock()) {
        this.construction = construction;
    } }

    public UpgradeResult upgrade(Player player,ProjectDefinition project){return upgradeAt(player,project,null);}
    public UpgradeResult upgradeAt(Player player, ProjectDefinition project, ru.neverland.townybuilds.construction.PlacementChoice choice) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(player);
        if (town == null) {
            return UpgradeResult.of(UpgradeResult.Status.NO_TOWN);
        }
        if (!towny.isMayor(player, town) && !ru.neverland.core.CouncilAccess.check(player, town.getUUID(), "build",false)) {
            return UpgradeResult.of(UpgradeResult.Status.NOT_MAYOR);
        }
        TownData data = dataStore.town(town.getUUID());
        int current = data.level(project.id());
        if (current >= project.maxLevel()) {
            return placePreviouslyUnlockedWonder(player, town, data, project, current, choice);
        }
        int next = current + 1;
        LevelDefinition level = project.level(next);
        if (level == null) {
            return UpgradeResult.of(UpgradeResult.Status.MAX_LEVEL);
        }
        List<String> missingPrerequisites = missingPrerequisites(town, project);
        if (!missingPrerequisites.isEmpty()) {
            return new UpgradeResult(UpgradeResult.Status.PREREQUISITES_NOT_MET, level.money(),
                    missingPrerequisites, next);
        }
        ConstructionPreparation preparation = null;
        boolean procedural = usesConstruction(project, next);
        if (project.type() == ProjectType.WONDER) {
            List<String> missingArtifacts = archaeology.missingLines(town.getUUID(), project.id());
            if (!missingArtifacts.isEmpty()) {
                return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_ARTIFACTS, level.money(), missingArtifacts, next);
            }
        }
        ResourceFund fund = data.resourceFund(project.id(), next);
        List<String> missing = missing(level.resources(), fund);
        if (!missing.isEmpty()) return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_RESOURCES, level.money(), missing, next);
        if (procedural) {
            if(choice==null && placements!=null){
                if(data.constructionSite(project.id())!=null&&data.constructionSite(project.id()).active())return UpgradeResult.of(UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS);
                placements.issue(player,town,project,next);
                return new UpgradeResult(UpgradeResult.Status.PLACEMENT_READY,level.money(),List.of(),next);
            }
            preparation=construction.prepare(player,town,project,current,next,choice);
            UpgradeResult failure=constructionFailure(preparation,level.money(),next);if(failure!=null)return failure;
        }
        if (!ru.neverland.townybuilds.construction.ConstructionRefunds.economyReady()) return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
        if (!town.getAccount().canPayFromHoldings(level.money())) {
            return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_MONEY, level.money(), List.of(), next);
        }
        if (!ru.neverland.integration.TreasuryAccess.canSpend(town,"construction",level.money())) return UpgradeResult.of(UpgradeResult.Status.BUDGET_UNAVAILABLE);
        String reason = plugin.getConfig().getString("settings.economy.withdraw-reason", "Городская постройка")
                .replace("{project}", ColorUtil.plain(project.name())).replace("{level}", String.valueOf(next));
        if (procedural && refunds != null) {
            try {
                boolean started=refunds.begin(player,town,project,level,preparation,fund,reason);
                if (!started) player.sendMessage("§eНе удалось начать стройку. Деньги и ресурсы поставлены на возврат в казну и склад.");
                return new UpgradeResult(started?UpgradeResult.Status.CONSTRUCTION_STARTED:UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS,level.money(),List.of(),next);
            } catch (Exception ex) {
                plugin.getLogger().warning("Старт стройки "+project.id()+": "+ex.getMessage());
                player.sendMessage("§eСтарт стройки приостановлен: "+ex.getMessage());
                return UpgradeResult.of(UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS);
            }
        }
        if (level.money() > 0 && !ru.neverland.integration.TreasuryAccess.withdraw(town,"construction","construction",level.money(),reason)) {
            return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
        }
        if (project.type() == ProjectType.WONDER && !archaeology.consume(town.getUUID(), project.id(), player.getUniqueId())) {
            if (level.money() > 0) ru.neverland.integration.TreasuryAccess.deposit(town,"construction","construction",true,level.money(),"Возврат: артефакты для Чуда Света не списаны");
            List<String> missingArtifacts = archaeology.missingLines(town.getUUID(), project.id());
            if (missingArtifacts.isEmpty()) missingArtifacts = List.of("Операция музея отменена");
            return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_ARTIFACTS, level.money(), missingArtifacts, next);
        }
        if (procedural) {
            try {
                construction.commit(preparation, player, town);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Не удалось активировать чертёж " + project.id() + ": " + exception.getMessage());
                if (level.money() > 0) ru.neverland.integration.TreasuryAccess.deposit(town,"construction","construction",true,level.money(),"Возврат: чертёж не активирован");
                return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
            }
        }
        for (ResourceFund.Snapshot surplus : fund.surplus(level.resources())) {
            int left = surplus.amount();
            ItemStack template = surplus.template();
            while (left > 0) {
                ItemStack stack = template.clone();
                int amount = Math.min(left, Math.max(1, stack.getMaxStackSize()));
                stack.setAmount(amount);
                player.getInventory().addItem(stack).values().forEach(extra ->
                        player.getWorld().dropItemNaturally(player.getLocation(), extra));
                left -= amount;
            }
        }
        data.clearResourceFund(project.id());
        if (procedural) {
            dataStore.markDirty();
            dataStore.save();
            return new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_STARTED, level.money(), List.of(), next);
        }
        data.setLevel(project.id(), next);
        town.setBonusBlocks(town.getBonusBlocks() + level.bonusBlocks());
        town.save();
        dataStore.markDirty();
        dataStore.save();
        runCommands(player, town, project.id(), level);
        if (project.type() == ProjectType.WONDER) {
            completeWonder(player, town, project);
        }
        return new UpgradeResult(UpgradeResult.Status.SUCCESS, level.money(), List.of(), next);
    } }

    public ConstructionProgress constructionProgress(Town town, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!usesConstruction(project)) {
            return new ConstructionProgress(false, 0, 0, 0, "");
        }
        return construction.progress(town, project.id());
    } }

    public boolean usesConstruction(ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return project != null && construction != null && construction.supports(project.id());
    } }

    public boolean usesConstruction(ProjectDefinition project, int targetLevel) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return project != null && construction != null && construction.supports(project.id(), targetLevel);
    } }

    public boolean needsPhysicalPlacement(Town town, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || project == null || project.type() != ProjectType.WONDER || !usesConstruction(project)) {
            return false;
        }
        TownData data = dataStore.town(town.getUUID());
        return data.level(project.id()) >= project.maxLevel() && data.constructionSite(project.id()) == null;
    } }

    public void completeConstruction(Player player, Town town, String projectId, int level) { synchronized (ru.neverland.core.SuiteState.lock()) {
        ProjectDefinition project = definitions.project(projectId);
        if (project == null || town == null) return;
        TownData data = dataStore.town(town.getUUID());
        if (data.level(projectId) >= level) {
            if (project.type() == ProjectType.WONDER) {
                messages.send(player, "wonder-placement-complete", Map.of("project", project.name()));
            }
            return;
        }
        LevelDefinition definition = project.level(level);
        if (definition == null) return;
        data.setLevel(projectId, level);
        town.setBonusBlocks(town.getBonusBlocks() + definition.bonusBlocks());
        town.save();
        dataStore.markDirty();
        dataStore.save();
        runCommands(player, town, project.id(), definition);
        messages.send(player, "construction-complete", Map.of("project", project.name(), "level", level));
        if (project.type() == ProjectType.WONDER) completeWonder(player, town, project);
    } }

    private UpgradeResult placePreviouslyUnlockedWonder(Player player, Town town, TownData data,
                                                        ProjectDefinition project, int current, ru.neverland.townybuilds.construction.PlacementChoice choice) {
        if (project.type() != ProjectType.WONDER || !usesConstruction(project)) {
            return UpgradeResult.of(UpgradeResult.Status.MAX_LEVEL);
        }
        var existing = data.constructionSite(project.id());
        if (existing != null) {
            return UpgradeResult.of(existing.active()
                    ? UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS : UpgradeResult.Status.MAX_LEVEL);
        }
        if(choice==null&&placements!=null){placements.issue(player,town,project,current);return new UpgradeResult(UpgradeResult.Status.PLACEMENT_READY,0,List.of(),current);}
        ConstructionPreparation preparation = construction.prepare(player, town, project, 0, current, choice);
        UpgradeResult failure = constructionFailure(preparation, 0, current);
        if (failure != null) return failure;
        try {
            if(refunds!=null)refunds.beginFree(player,town,preparation);
            else construction.commit(preparation, player, town);
            dataStore.markDirty();
            dataStore.save();
            return new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_STARTED, 0, List.of(), current);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Не удалось разместить ранее открытое Чудо " + project.id()
                    + ": " + exception.getMessage());
            return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
        }
    }

    private UpgradeResult constructionFailure(ConstructionPreparation preparation, double money, int level) {
        return switch (preparation.status()) {
            case READY -> null;
            case ALREADY_ACTIVE -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS, money, List.of(), level);
            case NO_GROUND_TARGET -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_NO_TARGET, money, preparation.details(), level);
            case OUTSIDE_TOWN -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_OUTSIDE_TOWN, money, preparation.details(), level);
            case UNEVEN_GROUND -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_UNEVEN_GROUND, money, preparation.details(), level);
            case OBSTRUCTED -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_OBSTRUCTED, money, preparation.details(), level);
            case UNKNOWN_BLUEPRINT -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_UNAVAILABLE, money, preparation.details(), level);
        };
    }

    public List<String> artifactRequirements(Town town, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || project == null || project.type() != ProjectType.WONDER) return List.of();
        return archaeology.requirementLines(town.getUUID(), project.id());
    } }

    public List<String> prerequisiteRequirements(Town town, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || project == null) return List.of();
        TownData data = dataStore.town(town.getUUID());
        List<String> lines = new ArrayList<>();
        if(!ru.neverland.integration.SpecializationRules.required(project.id()).isEmpty())lines.add((ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),project.id())?"&a✔ ":"&c✘ ")+"Специализация: "+ru.neverland.integration.SpecializationAccess.requirement(project.id()));
        for (Map.Entry<String, Integer> requirement : project.requirements().entrySet()) {
            ProjectDefinition dependency = definitions.project(requirement.getKey());
            String name = dependency == null ? requirement.getKey() : ColorUtil.plain(dependency.name());
            int current = data.level(requirement.getKey());
            String color = current >= requirement.getValue() ? "&a✔ " : "&c✘ ";
            lines.add(color + name + " &7" + current + "/" + requirement.getValue());
        }
        return lines;
    } }

    private List<String> missingPrerequisites(Town town, ProjectDefinition project) {
        TownData data = dataStore.town(town.getUUID());
        List<String> missing = new ArrayList<>();
        if(!ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),project.id()))missing.add(ru.neverland.integration.SpecializationAccess.reason(project.id()));
        for (Map.Entry<String, Integer> requirement : project.requirements().entrySet()) {
            int current = data.level(requirement.getKey());
            if (current >= requirement.getValue()) continue;
            ProjectDefinition dependency = definitions.project(requirement.getKey());
            String name = dependency == null ? requirement.getKey() : ColorUtil.plain(dependency.name());
            missing.add(name + " " + current + "/" + requirement.getValue());
        }
        return missing;
    }

    public int contributed(Town town, ProjectDefinition project, int targetLevel, ItemStack required) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (town == null || project == null || required == null) return 0;
        TownData data = dataStore.town(town.getUUID());
        ResourceFund fund = data.existingResourceFund(project.id());
        if (fund == null || fund.targetLevel() != targetLevel) return 0;
        return Math.min(required.getAmount(), fund.count(required));
    } }

    public List<String> unavailableReasons(Player player,ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        var town=towny.town(player);if(town==null)return List.of("Вступите в город, чтобы строить городские здания.");
        if(!dataStore.writable())return List.of("Сохранение построек временно недоступно. Попробуйте позже.");
        var state=dataStore.town(town.getUUID());var reasons=new ArrayList<String>();
        var active=state.constructionSite(project.id());if(active!=null&&active.active())return List.of("Строительство уже идёт. Завершите текущую постройку.");
        if(!towny.isMayor(player,town)&&!ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"build",false))reasons.add("Попросите мэра разрешить вам строительство.");
        if(refunds!=null&&refunds.blocks(town.getUUID(),project.id()))reasons.add("Дождитесь завершения возврата за предыдущую стройку.");
        int next=state.level(project.id())+1;var level=project.level(next);
        if(level==null){if(!needsPhysicalPlacement(town,project))reasons.add("Здание уже достигло максимального уровня.");return reasons;}
        reasons.addAll(missingPrerequisites(town,project).stream().map(x->"Сначала выполните условие: "+ColorUtil.plain(x)).toList());
        var fund=state.existingResourceFund(project.id());
        for(var required:level.resources()){
            int have=fund!=null&&fund.targetLevel()==next?fund.count(required):0;
            if(have<required.getAmount())reasons.add("Сдайте материалы: "+itemNames.name(required)+" ×"+(required.getAmount()-have));
        }
        if(project.type()==ProjectType.WONDER)reasons.addAll(archaeology.missingLines(town.getUUID(),project.id()).stream().map(x->"Передайте в музей: "+ColorUtil.plain(x)).toList());
        // A blueprint can be obtained before payment; money is checked when its position is confirmed.
        if(!usesConstruction(project,next)&&level.money()>0){
            if(!ru.neverland.townybuilds.construction.ConstructionRefunds.economyReady())reasons.add("Банк временно недоступен. Деньги пока не списываются.");
            else if(!town.getAccount().canPayFromHoldings(level.money()))reasons.add("Пополните казну: для здания нужно "+level.money()+" монет.");
            else if(!ru.neverland.integration.TreasuryAccess.canSpend(town,"construction",level.money()))reasons.add("Попросите мэра выделить деньги на строительство в бюджете города.");
        }
        return List.copyOf(reasons);
    } }

    public ContributionPreview previewContribution(Player player,ProjectDefinition project,boolean warehouse) { synchronized (ru.neverland.core.SuiteState.lock()) {
        var town=towny.town(player);var lines=new ArrayList<ContributionPreview.Line>();String reason="";int target=0;
        String signature="";
        if(town==null)reason="Вступите в город, чтобы сдавать материалы для его зданий.";
        else if(!dataStore.writable())reason="Сохранение материалов временно недоступно. Попробуйте позже.";
        else {
            var state=dataStore.town(town.getUUID());target=state.level(project.id())+1;var level=project.level(target);var site=state.constructionSite(project.id());
            if(!ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),project.id()))reason=ru.neverland.integration.SpecializationAccess.reason(project.id());
            else if(warehouse&&!towny.isMayor(player,town)&&!ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"build_resources",false))reason="Попросите мэра разрешить вам сдачу материалов со склада города.";
            else if(site!=null&&site.active()||refunds!=null&&refunds.blocks(town.getUUID(),project.id()))reason="Сначала завершите стройку или дождитесь возврата за её отмену.";
            else if(level==null)reason="Здание уже достигло максимального уровня.";
            else if(warehouse&&dataStore.storageBusy(town.getUUID(),"warehouse"))reason="Закройте склад города перед сдачей материалов.";
            var personal=cloneContents(player.getInventory().getStorageContents());var stored=cloneContents(state.storage());
            var available=cloneContents(warehouse?stored:personal);var fund=state.existingResourceFund(project.id());
            var fingerprint=new StringBuilder(player.getUniqueId()+":"+town.getUUID()+":"+project.id()+":"+target+":"+warehouse);
            if(level!=null)for(var required:level.resources()){
                int have=fund!=null&&fund.targetLevel()==target?fund.count(required):0;
                int needed=Math.max(0,required.getAmount()-have),taking=Math.min(needed,ResourceTransfer.count(available,required));
                ResourceTransfer.remove(available,required,taking);
                lines.add(new ContributionPreview.Line(required,itemNames.name(required),needed,ResourceTransfer.count(personal,required),ResourceTransfer.count(stored,required),taking));
                fingerprint.append(':').append(java.util.Base64.getEncoder().encodeToString(required.serializeAsBytes())).append(':').append(have).append(':').append(taking);
            }
            for(var item:warehouse?stored:personal)fingerprint.append('|').append(item==null?"-":java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            signature=java.util.UUID.nameUUIDFromBytes(fingerprint.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            if(reason.isEmpty()&&lines.stream().allMatch(x->x.needed()==0))reason="Все необходимые материалы уже собраны.";
            else if(reason.isEmpty()&&lines.stream().allMatch(x->x.taking()==0))reason=warehouse?"На складе нет подходящих материалов из списка.":"В вашем инвентаре нет подходящих материалов из списка.";
        }
        return new ContributionPreview(player.getUniqueId(),town==null?null:town.getUUID(),project.id(),target,warehouse,signature,reason,lines);
    } }

    public ContributionResult confirmContribution(Player player,ProjectDefinition project,ContributionPreview quote) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(!quote.player().equals(player.getUniqueId())||!quote.project().equals(project.id()))return ContributionResult.of(ContributionResult.Status.PREVIEW_CHANGED);
        var fresh=previewContribution(player,project,quote.warehouse());
        if(!fresh.ready()||!fresh.signature().equals(quote.signature()))return ContributionResult.of(ContributionResult.Status.PREVIEW_CHANGED);
        return quote.warehouse()?contributeFromStorage(player,project):contributeFromPlayer(player,project);
    } }

    public ContributionResult contributeFromPlayer(Player player, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(player);
        if (town == null) return ContributionResult.of(ContributionResult.Status.NO_TOWN);
        return contribute(player, town, project, false);
    } }

    public ContributionResult contributeFromStorage(Player player, ProjectDefinition project) { synchronized (ru.neverland.core.SuiteState.lock()) {
        Town town = towny.town(player);
        if (town == null) return ContributionResult.of(ContributionResult.Status.NO_TOWN);
        if (!towny.isMayor(player, town) && !ru.neverland.core.CouncilAccess.check(player, town.getUUID(), "build_resources",false)) return ContributionResult.of(ContributionResult.Status.NOT_MAYOR);
        return contribute(player, town, project, true);
    } }

    private ContributionResult contribute(Player player, Town town, ProjectDefinition project, boolean cityStorage) {
        if(!ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),project.id()))return new ContributionResult(ContributionResult.Status.SPECIALIZATION_LOCKED,0,List.of(ru.neverland.integration.SpecializationAccess.reason(project.id())));
        if(cityStorage&&dataStore.storageBusy(town.getUUID(),"warehouse"))return ContributionResult.of(ContributionResult.Status.STORAGE_BUSY);
        TownData data = dataStore.town(town.getUUID());
        int targetLevel = data.level(project.id()) + 1;
        var activeSite=data.constructionSite(project.id());
        if(activeSite!=null&&activeSite.active()||refunds!=null&&refunds.blocks(town.getUUID(),project.id()))
            return ContributionResult.of(ContributionResult.Status.CONSTRUCTION_ACTIVE);
        LevelDefinition level = project.level(targetLevel);
        if (level == null) return ContributionResult.of(ContributionResult.Status.MAX_LEVEL);
        if (level.resources().isEmpty()) return ContributionResult.of(ContributionResult.Status.NOTHING_NEEDED);

        ResourceFund fund = data.resourceFund(project.id(), targetLevel);
        List<ResourceFund.Snapshot> originalFund = fund.entries();
        ItemStack[] originalContents = cloneContents(cityStorage ? data.storage() : player.getInventory().getStorageContents());
        ItemStack[] contents = cityStorage
                ? data.storage()
                : cloneContents(player.getInventory().getStorageContents());
        List<PendingContribution> pending = new ArrayList<>();
        List<String> details = new ArrayList<>();

        for (ItemStack required : level.resources()) {
            int needed = Math.max(0, required.getAmount() - fund.count(required));
            if (needed == 0) continue;
            int before = ResourceTransfer.count(contents, required);
            int requested = Math.min(before, needed);
            if (requested <= 0) continue;
            int notRemoved = ResourceTransfer.remove(contents, required, requested);
            int removed = requested - notRemoved;
            if (removed > 0) pending.add(new PendingContribution(required, removed));
        }
        if (pending.isEmpty()) return ContributionResult.of(ContributionResult.Status.NOTHING_MATCHED);

        if (cityStorage) {
            data.setStorage(contents, plugin.getConfig().getInt("settings.storage.size", 54));
        } else {
            Map<ItemStack, Integer> beforeCounts = new java.util.IdentityHashMap<>();
            for (PendingContribution contribution : pending) {
                beforeCounts.put(contribution.template(), ResourceTransfer.count(player.getInventory().getStorageContents(), contribution.template()));
            }
            try {
                player.getInventory().setStorageContents(contents);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Не удалось синхронизировать взнос игрока " + player.getName()
                        + ": " + exception.getMessage());
                try { player.getInventory().setStorageContents(originalContents); }
                catch(RuntimeException restore){plugin.getLogger().severe("Не удалось восстановить инвентарь после ошибки взноса: "+restore.getMessage());}
                return ContributionResult.of(ContributionResult.Status.INVENTORY_SYNC_FAILED);
            }
            ItemStack[] actual = player.getInventory().getStorageContents();
            List<PendingContribution> verified = new ArrayList<>();
            for (PendingContribution contribution : pending) {
                int before = beforeCounts.getOrDefault(contribution.template(), 0);
                int after = ResourceTransfer.count(actual, contribution.template());
                int removed = Math.min(contribution.amount(), Math.max(0, before - after));
                if (removed > 0) verified.add(new PendingContribution(contribution.template(), removed));
            }
            pending = verified;
            if (pending.isEmpty()) {
                return ContributionResult.of(ContributionResult.Status.INVENTORY_SYNC_FAILED);
            }
        }

        int total = 0;
        for (PendingContribution contribution : pending) {
            fund.add(contribution.template(), contribution.amount());
            total += contribution.amount();
            details.add(itemNames.name(contribution.template()) + " x" + contribution.amount());
        }
        dataStore.markDirty();
        try { dataStore.saveOrThrow(); }
        catch (java.io.IOException | RuntimeException failure) {
            data.clearResourceFund(project.id());
            ResourceFund restored=data.resourceFund(project.id(),targetLevel);
            originalFund.forEach(entry->restored.add(entry.template(),entry.amount()));
            if(cityStorage)data.setStorage(originalContents,plugin.getConfig().getInt("settings.storage.size",54));
            else player.getInventory().setStorageContents(originalContents);
            plugin.getLogger().severe("Взнос отменён: не удалось сохранить фонд; предметы возвращены. "+failure.getMessage());
            player.sendMessage("§eРесурсы возвращены: сервер не смог сохранить взнос. Сообщите администратору.");
            return ContributionResult.of(ContributionResult.Status.INVENTORY_SYNC_FAILED);
        }
        String receipt=java.util.UUID.randomUUID().toString();var person=ru.neverland.core.AuditTrail.player(player.getUniqueId());var city=ru.neverland.core.AuditTrail.town(town.getUUID());
        int line=0;for(var contribution:pending)ru.neverland.core.AuditTrail.record(plugin,"contribution:"+(line++),receipt,"BUILD_CONTRIBUTION","COMPLETED",person,cityStorage?city:person,city,
            ru.neverland.core.AuditTrail.item(contribution.template()),contribution.amount(),"",ColorUtil.plain(project.name())+" • уровень "+targetLevel+" • "+(cityStorage?"Со склада города":"Из инвентаря"));
        if(placements!=null&&usesConstruction(project,targetLevel)&&missing(level.resources(),fund).isEmpty()) {
            try {placements.issue(player,town,project,targetLevel);}catch(RuntimeException e){plugin.getLogger().warning("Ресурсы сохранены, выдача чертежа отложена: "+e.getMessage());player.sendMessage("§eРесурсы сохранены. Чертёж можно получить в меню здания.");}
        }
        return new ContributionResult(ContributionResult.Status.SUCCESS, total, details);
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    private List<String> missing(List<ItemStack> requirements, ResourceFund fund) {
        List<String> missing = new ArrayList<>();
        for (ItemStack required : requirements) {
            int found = fund.count(required);
            if (found < required.getAmount()) {
                missing.add(itemNames.name(required) + " x" + (required.getAmount() - found));
            }
        }
        return missing;
    }

    private record PendingContribution(ItemStack template, int amount) { }

    private void runCommands(Player player, Town town, String projectId, LevelDefinition level) {var commands=new java.util.ArrayList<String>();
        for(String command:level.commands())commands.add(command.replace("{player}",player.getName()).replace("{town}",town.getName()).replace("{level}",String.valueOf(level.level())));
        ru.neverland.core.CommandOutbox.enqueue(plugin,"building:"+town.getUUID()+":"+projectId+":"+level.level(),commands);
    }

    private void completeWonder(Player player, Town town, ProjectDefinition project) {
        if(!ru.neverland.core.SuiteScheduler.owns(player)){ru.neverland.core.SuiteScheduler.player(plugin,player,p->completeWonder(p,town,project));return;}
        messages.send(player, "wonder-complete-local", Map.of("project", project.name()));
        if (plugin.getConfig().getBoolean("settings.announcements.wonder-complete", true)) {
            Bukkit.broadcast(messages.component("wonder-broadcast", Map.of("town", town.getName(), "project", project.name())));
        }
        if (!plugin.getConfig().getBoolean("settings.announcements.fireworks", true)) {
            return;
        }
        int count = Math.max(1, plugin.getConfig().getInt("settings.announcements.firework-count", 8));
        Location origin = player.getLocation().clone();
        for (int index = 0; index < count; index++) {
            ru.neverland.core.SuiteScheduler.regionLater(plugin, origin, () -> launchFirework(origin), index * 8L);
        }
    }

    private void launchFirework(Location origin) {
        if (origin.getWorld() == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = origin.clone().add(random.nextDouble(-4, 4), 1, random.nextDouble(-4, 4));
        if(!ru.neverland.core.SuiteScheduler.owns(location))return;
        Firework firework = origin.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder().flicker(true).trail(true)
                .with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                .withColor(Color.fromRGB(random.nextInt(80, 256), random.nextInt(60, 220), random.nextInt(120, 256)))
                .withFade(Color.AQUA).build());
        firework.setFireworkMeta(meta);
    }

}
