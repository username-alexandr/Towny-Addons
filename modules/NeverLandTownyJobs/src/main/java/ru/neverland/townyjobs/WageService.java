package ru.neverland.townyjobs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PlayerTradeEvent;
import ru.neverland.core.*;
import ru.neverland.earnings.*;

/** Salary observes completed production. Movement, worksite presence and XP signals never pay. */
public final class WageService implements Listener,CommandExecutor,TabCompleter {
    private final JavaPlugin plugin;private final JobsService jobs;private final IncomeRuntime income;private final ActionGuard guard;
    private final Map<Profession,Long> rates=new EnumMap<>(Profession.class);private final List<Double> multipliers;
    private final Map<UUID,Long> last=new HashMap<>();private final long cooldown;
    private final Map<String,String> crops=new HashMap<>();private final java.nio.file.Path cropFile;
    private final Map<String,Long> fertilized=new HashMap<>();
    private final Map<String,Brewer> brewers=new HashMap<>();private final Map<UUID,Location> lastKill=new HashMap<>();private boolean cropFault;
    private record Brewer(UUID player,long at,ItemStack ingredient){}
    public WageService(JavaPlugin plugin,JobsService jobs)throws Exception{
        this.plugin=plugin;this.jobs=jobs;
        if(!plugin.getDataFolder().toPath().resolve("salary.yml").toFile().exists())plugin.saveResource("salary.yml",false);
        var cfg=SafeYaml.load(plugin.getDataFolder().toPath().resolve("salary.yml"));
        income=new IncomeRuntime(plugin,"salary-payments",money(cfg,"limits.player-24h",350),money(cfg,"limits.server-24h",25000),cfg.getBoolean("enabled",true));
        multipliers=cfg.getDoubleList("level-multipliers");if(multipliers.size()!=5||multipliers.stream().anyMatch(n->!Double.isFinite(n)||n<1||n>2))throw new IllegalArgumentException("Нужны пять множителей зарплаты 1..2");
        for(int i=1;i<5;i++)if(multipliers.get(i)<multipliers.get(i-1))throw new IllegalArgumentException("Множители должны возрастать");
        cooldown=SafeYaml.intValue(cfg,"minimum-action-seconds",5)*1000L;if(cooldown<1000||cooldown>300000)throw new IllegalArgumentException("Пауза между оплачиваемыми действиями 1..300 секунд");
        for(Profession p:Profession.values())rates.put(p,money(cfg,"pay."+p.id(),1));
        for(var e:income.ledger().all())last.merge(e.player(),e.created(),Math::max);
        cropFile=plugin.getDataFolder().toPath().resolve("salary-crops.yml");var y=SafeYaml.load(cropFile);if(y.contains("schema")&&SafeYaml.intValue(y,"schema")!=1)throw new IOException("Неизвестный формат происхождения урожая");var s=SafeYaml.section(y,"crops");if(s!=null)for(String key:s.getKeys(false))crops.put(key,SafeYaml.text(s,key));AtomicFiles.loaded(cropFile);
        guard=new ActionGuard(plugin,"salary-blocks.log");plugin.getServer().getPluginManager().registerEvents(guard,plugin);plugin.getServer().getPluginManager().registerEvents(this,plugin);
        var command=Objects.requireNonNull(plugin.getCommand("jobpay"));command.setExecutor(this);command.setTabCompleter(this);income.start();
    }
    private static long money(org.bukkit.configuration.ConfigurationSection c,String key,int d){double n=SafeYaml.doubleValue(c,key,d);if(n<=0||n>1000000||Math.abs(n*100-Math.rint(n*100))>0.00001)throw new IllegalArgumentException("Некорректная зарплата "+key);return Math.round(n*100);}
    private boolean eligible(Player p,Profession profession){
        if(!income.enabled()||!ActionGuard.survival(p)||!p.hasPermission("neverlandtownyjobs.earn")||jobs.career(p.getUniqueId()).profession()!=profession||!jobs.onDuty(p.getUniqueId()))return false;
        // Real action refreshes activity, but the actual shift still has to pass warmup/worksite checks.
        jobs.signal(p.getUniqueId(),profession);return jobs.status(p.getUniqueId()).equals("На смене");
    }
    private void pay(Player p,Profession profession,String proof){synchronized(SuiteState.lock()){
        if(!eligible(p,profession))return;long now=System.currentTimeMillis();if(now-last.getOrDefault(p.getUniqueId(),0L)<cooldown)return;
        UUID id=UUID.nameUUIDFromBytes(("salary:"+p.getUniqueId()+":"+proof).getBytes(StandardCharsets.UTF_8));if(income.ledger().get(id)!=null)return;
        long amount=Math.round(rates.get(profession)*multipliers.get(jobs.settings().level(jobs.career(p.getUniqueId()))-1));
        if(income.ledger().remaining(p.getUniqueId())<amount)return;
        try{income.ledger().reserve(id,p.getUniqueId(),amount,"jobs:"+profession.id()+":"+proof,false);last.put(p.getUniqueId(),now);}
        catch(IOException ex){plugin.getLogger().severe("Зарплата приостановлена: "+ex.getMessage());}
    }}
    @EventHandler(priority=EventPriority.MONITOR) public void smelt(FurnaceExtractEvent e){synchronized(SuiteState.lock()){
        if(e.getItemAmount()<=0||e.getExpToDrop()<=0||!Set.of(Material.IRON_INGOT,Material.COPPER_INGOT,Material.GOLD_INGOT,Material.NETHERITE_SCRAP).contains(e.getItemType()))return;
        // Extraction is a completed inventory operation. Reinserting output has no smelting XP.
        pay(e.getPlayer(),Profession.BLACKSMITH,"smelt:"+UUID.randomUUID());
    }}
    private void saveCrops(){var y=new YamlConfiguration();y.set("schema",1);crops.forEach((k,v)->y.set("crops."+k,v));try{AtomicFiles.write(cropFile,y::saveToString);}catch(IOException ex){cropFault=true;plugin.getLogger().severe("Зарплата фермера остановлена: "+ex.getMessage());}}
    private void clearCrop(Block b){if(crops.remove(ActionGuard.key(b))!=null)saveCrops();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void grow(BlockGrowEvent e){
        if(!(e.getNewState().getBlockData() instanceof Ageable age)||age.getAge()!=age.getMaximumAge())return;
        Location location=e.getBlock().getLocation();SuiteScheduler.region(plugin,location,()->{if(e.isCancelled()||cropFault||!(location.getBlock().getBlockData() instanceof Ageable current)||current.getAge()!=current.getMaximumAge())return;String key=ActionGuard.key(location.getBlock());if(System.currentTimeMillis()-fertilized.getOrDefault(key,0L)<60000)return;crops.putIfAbsent(key,UUID.randomUUID().toString());saveCrops();});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){synchronized(SuiteState.lock()){for(BlockState b:e.getBlocks()){fertilized.put(ActionGuard.key(b.getBlock()),System.currentTimeMillis());clearCrop(b.getBlock());}}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){synchronized(SuiteState.lock()){clearCrop(e.getBlock());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void harvest(BlockBreakEvent e){synchronized(SuiteState.lock()){
        String proof=crops.remove(ActionGuard.key(e.getBlock()));if(proof==null)return;saveCrops();
        if(cropFault||!e.isDropItems()||!(e.getBlock().getBlockData() instanceof Ageable age)||age.getAge()!=age.getMaximumAge())return;
        Player p=e.getPlayer();Material before=e.getBlock().getType();Location at=e.getBlock().getLocation();
        SuiteScheduler.region(plugin,at,()->{if(!e.isCancelled()&&at.getBlock().getType()!=before)SuiteScheduler.entity(plugin,p,()->pay(p,Profession.FARMER,"harvest:"+proof));});
    }}
    private static int count(Player p,Material material){int n=0;for(ItemStack item:p.getInventory().getStorageContents())if(item!=null&&item.getType()==material)n+=item.getAmount();if(p.getItemOnCursor().getType()==material)n+=p.getItemOnCursor().getAmount();return n;}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void craft(CraftItemEvent e){synchronized(SuiteState.lock()){
        if(!(e.getWhoClicked() instanceof Player p)||e.getAction()==InventoryAction.NOTHING)return;
        Material result=e.getRecipe().getResult().getType();if(!Set.of(Material.PISTON,Material.DISPENSER,Material.DROPPER,Material.REPEATER,Material.COMPARATOR).contains(result)||!eligible(p,Profession.ENGINEER))return;
        int before=count(p,result);CraftingInventory inventory=e.getInventory();ItemStack[] ingredients=inventory.getMatrix();for(int i=0;i<ingredients.length;i++)if(ingredients[i]!=null)ingredients[i]=ingredients[i].clone();String proof="craft:"+UUID.randomUUID();
        SuiteScheduler.entity(plugin,p,()->{if(e.isCancelled()||!p.getOpenInventory().getTopInventory().equals(inventory)||count(p,result)<=before)return;ItemStack[] after=inventory.getMatrix();boolean consumed=false;for(int i=0;i<Math.min(ingredients.length,after.length);i++)if(ingredients[i]!=null&&(after[i]==null||!ingredients[i].isSimilar(after[i])||after[i].getAmount()<ingredients[i].getAmount()))consumed=true;if(consumed)pay(p,Profession.ENGINEER,proof);});
    }}
    @EventHandler(priority=EventPriority.MONITOR) public void kill(EntityDeathEvent e){synchronized(SuiteState.lock()){
        if(!guard.hunted(e))return;Player p=e.getEntity().getKiller();Location before=lastKill.get(p.getUniqueId()),now=p.getLocation();
        if(before!=null&&before.getWorld().equals(now.getWorld())&&before.distanceSquared(now)<16)return;
        lastKill.put(p.getUniqueId(),now);pay(p,Profession.GUARD,"kill:"+e.getEntity().getUniqueId());
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void trade(PlayerTradeEvent e){synchronized(SuiteState.lock()){
        Player p=e.getPlayer();var villager=e.getVillager();if(!SuiteScheduler.owns(villager)||!e.willIncreaseTradeUses()||!eligible(p,Profession.MERCHANT))return;
        // Pay selling goods to a real villager, never buying/re-selling emeralds or custom menus.
        MerchantRecipe recipe=e.getTrade();if(recipe.getResult().getType()!=Material.EMERALD||recipe.getIngredients().stream().anyMatch(i->i.getType()==Material.EMERALD))return;
        if(!(p.getOpenInventory().getTopInventory() instanceof MerchantInventory inventory))return;int index=inventory.getSelectedRecipeIndex();if(index<0||index>=villager.getRecipeCount())return;int before=villager.getRecipe(index).getUses();UUID npc=villager.getUniqueId();
        SuiteScheduler.entity(plugin,villager,()->{if(e.isCancelled()||index>=villager.getRecipeCount()||villager.getRecipe(index).getUses()<=before)return;String proof="trade:"+npc+":"+(System.currentTimeMillis()/RewardLedger.DAY)+":"+index+":"+(before+1);SuiteScheduler.entity(plugin,p,()->pay(p,Profession.MERCHANT,proof));});
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void enchant(EnchantItemEvent e){synchronized(SuiteState.lock()){
        Player p=e.getEnchanter();if(e.getExpLevelCost()<=0||e.getEnchantsToAdd().isEmpty()||!eligible(p,Profession.RESEARCHER))return;
        var inventory=e.getInventory();ItemStack base=e.getItem().clone();var enchants=new HashMap<>(e.getEnchantsToAdd());
        String proof="enchant:"+base.getType()+":"+new TreeMap<>(enchants.entrySet().stream().collect(java.util.stream.Collectors.toMap(v->v.getKey().getKey().toString(),Map.Entry::getValue)))+":"+(System.currentTimeMillis()/RewardLedger.DAY);
        SuiteScheduler.entity(plugin,p,()->{if(e.isCancelled()||!p.getOpenInventory().getTopInventory().equals(inventory))return;ItemStack item=inventory.getItem(0);if(item!=null&&(item.getType()==base.getType()||base.getType()==Material.BOOK&&item.getType()==Material.ENCHANTED_BOOK)&&(item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta stored?stored.getStoredEnchants():item.getEnchantments()).entrySet().containsAll(enchants.entrySet()))pay(p,Profession.RESEARCHER,proof);});
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void ingredient(InventoryClickEvent e){synchronized(SuiteState.lock()){
        if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory() instanceof BrewerInventory inv)||!eligible(p,Profession.ALCHEMIST)||inv.getLocation()==null)return;
        // Attribute only an observed increase of the ingredient slot caused by this player's click.
        ItemStack before=inv.getIngredient()==null?null:inv.getIngredient().clone();Location at=inv.getLocation();UUID actor=p.getUniqueId();
        SuiteScheduler.region(plugin,at,()->{if(e.isCancelled()||!(at.getBlock().getState() instanceof BrewingStand stand))return;ItemStack next=stand.getInventory().getIngredient();if(next!=null&&(before==null||!next.isSimilar(before)||next.getAmount()>before.getAmount()))brewers.put(ActionGuard.key(at.getBlock()),new Brewer(actor,System.currentTimeMillis(),next.clone()));});
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){synchronized(SuiteState.lock()){
        if(e.getDestination() instanceof BrewerInventory inv&&inv.getLocation()!=null)brewers.remove(ActionGuard.key(inv.getLocation().getBlock()));
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void brew(BrewEvent e){synchronized(SuiteState.lock()){
        Brewer brewer=brewers.remove(ActionGuard.key(e.getBlock()));if(brewer==null||System.currentTimeMillis()-brewer.at()>120000)return;
        ItemStack ingredient=e.getContents().getIngredient();if(ingredient==null||!ingredient.isSimilar(brewer.ingredient()))return;
        var results=e.getResults().stream().map(i->i==null?null:i.clone()).toList();boolean changed=false;for(int i=0;i<Math.min(3,results.size());i++)if(results.get(i)!=null&&!results.get(i).equals(e.getContents().getItem(i)))changed=true;if(!changed)return;
        Location at=e.getBlock().getLocation();String proof="brew:"+UUID.randomUUID();
        SuiteScheduler.region(plugin,at,()->{if(e.isCancelled()||!(at.getBlock().getState() instanceof BrewingStand stand))return;boolean match=false;for(int i=0;i<Math.min(3,results.size());i++)if(results.get(i)!=null&&results.get(i).equals(stand.getInventory().getItem(i)))match=true;if(!match)return;Player p=Bukkit.getPlayer(brewer.player());if(p!=null)SuiteScheduler.entity(plugin,p,()->pay(p,Profession.ALCHEMIST,proof));});
    }}
    @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] args){synchronized(SuiteState.lock()){try{
        if(args.length>0&&args[0].equals("admin")){if(!sender.isOp()&&!sender.hasPermission("neverlandtownyjobs.payments"))throw new IllegalArgumentException("Нет прав на сверку");income.admin(sender,Arrays.copyOfRange(args,1,args.length));return true;}
        if(!(sender instanceof Player p))throw new IllegalArgumentException("/jobpay admin status");if(!p.hasPermission("neverlandtownyjobs.earn"))throw new IllegalArgumentException("Нет прав на зарплату");
        sender.sendMessage("Зарплата: "+(income.enabled()?"включена":"приостановлена")+". Остаток лимита 24ч: "+AuditTrail.cents(income.ledger().remaining(p.getUniqueId())));
        sender.sendMessage("Рабочая смена: "+jobs.status(p.getUniqueId())+". Зарплата начисляется за результат работы, а не за время на месте.");
        income.ledger().all().stream().filter(e->e.player().equals(p.getUniqueId())).sorted(java.util.Comparator.comparingLong(RewardLedger.Entry::created).reversed()).limit(5).forEach(e->sender.sendMessage(e.id()+" "+e.state()+" "+AuditTrail.cents(e.cents())));
    }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());}return true;}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String label,String[] args){return args.length==1?List.of("status","admin"):args.length==2&&args[0].equals("admin")?List.of("status","list","show","resolve"):List.of();}
}
