package cc.crafto.ui.probe;
import cc.crafto.ui.*;
import com.google.gson.JsonArray;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static cc.crafto.ui.Protocol.object;

/** LOCAL TEST FIXTURE ONLY. Never install on a public server: commands deliberately alter test permissions. */
public final class ApiProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private final Map<UUID,PermissionAttachment> permissions=new ConcurrentHashMap<>();
    private final AtomicInteger count=new AtomicInteger();
    @Override public void onEnable(){
        getServer().getPluginManager().registerEvents(this,this);
        CraftoUIApi api=Bukkit.getServicesManager().load(CraftoUIApi.class);
        if(api==null)throw new IllegalStateException("CraftoUI service missing");
        api.registerAction(this,"probe:increment","probe.action",ctx->{
            if(!Bukkit.isOwnedByCurrentRegion(ctx.player()))throw new AssertionError("Wrong entity thread");
            return CraftoUIApi.ActionResult.ok("counter="+count.incrementAndGet());
        });
        api.registerDataProvider(this,"probe",snapshot->CompletableFuture.completedFuture(Map.of("uuid",snapshot.uuid().toString(),"thread",Thread.currentThread().getName())));
        JsonArray components=new JsonArray();
        components.add(object("id","count","type","button","x",20,"y",80,"w",300,"h",40,"text","API action","action","probe:increment"));
        components.add(object("id","data","type","text","x",20,"y",140,"w",300,"h",30,"text","${probe.uuid}"));
        api.registerMenu(this,object("id","probe","title","API probe","width",560,"height",460,"theme","classic","permission","craftoui.use","components",components));
        Objects.requireNonNull(getCommand("craftouiprobe")).setExecutor((sender,command,label,args)->{
            if(!(sender instanceof Player p))return true;
            p.getScheduler().run(this,t->{
                PermissionAttachment a=permissions.computeIfAbsent(p.getUniqueId(),id->p.addAttachment(this));
                String mode=args.length==0?"allow":args[0];
                if(mode.equals("deny"))a.setPermission("craftoui.profile",false);
                if(mode.equals("allow"))a.setPermission("craftoui.profile",true);
                if(mode.equals("admin"))a.setPermission("craftoui.admin.reload",true);
                if(mode.equals("action"))a.setPermission("probe.action",true);
                if(mode.equals("revoke"))a.setPermission("probe.action",false);
                if(mode.equals("native"))nativeMenu(p,false);
                if(mode.equals("storage"))nativeMenu(p,true);
                if(mode.equals("unlisted"))p.openInventory(Bukkit.createInventory(null,54,net.kyori.adventure.text.Component.text("Проверка настоящих кликов")));
                p.sendMessage("probe:"+mode);
            },null);return true;
        });
    }

    public static final class NativeHolder implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inventory;
        public org.bukkit.inventory.Inventory getInventory(){return inventory;}
    }
    public static final class StorageHolder implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inventory;
        public org.bukkit.inventory.Inventory getInventory(){return inventory;}
    }
    private void nativeMenu(Player p,boolean storage){
        var holder=storage?new StorageHolder():new NativeHolder();
        var inventory=Bukkit.createInventory(holder,54,net.kyori.adventure.text.Component.text(storage?"Тестовый склад":"Проверка настоящих кликов"));
        if(holder instanceof NativeHolder h)h.inventory=inventory;else ((StorageHolder)holder).inventory=inventory;
        if(storage){inventory.setItem(0,new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND,7));inventory.setItem(1,new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE,12));}
        else for(int i=0;i<32;i++){
            var item=new org.bukkit.inventory.ItemStack(i%2==0?org.bukkit.Material.EMERALD:org.bukkit.Material.PAPER);
            var meta=item.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Действие "+i));
            meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("ЛКМ и ПКМ передаются серверу"),net.kyori.adventure.text.Component.text("Shift также сохраняется"),net.kyori.adventure.text.Component.text("Это тестовая карточка, не игровая награда")));item.setItemMeta(meta);inventory.setItem(i,item);
        }
        p.openInventory(inventory);
    }
    @org.bukkit.event.EventHandler public void click(org.bukkit.event.inventory.InventoryClickEvent e){
        var holder=e.getView().getTopInventory().getHolder(false);
        if(holder instanceof NativeHolder){e.setCancelled(true);getLogger().info("NATIVE_CLICK slot="+e.getRawSlot()+" click="+e.getClick()+" owned="+Bukkit.isOwnedByCurrentRegion((Player)e.getWhoClicked()));}
        if(holder instanceof StorageHolder)getLogger().info("STORAGE_CLICK slot="+e.getRawSlot()+" click="+e.getClick());
        if(holder!=null&&holder.getClass().getName().startsWith("ru.neverland."))getLogger().info("ADDON_CLICK holder="+holder.getClass().getName()+" slot="+e.getRawSlot());
    }
}
