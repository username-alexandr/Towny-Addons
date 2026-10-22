package ru.neverland.earnings;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;

/** Actual API event objects, strict entity doubles: remote player state access fails the test. */
public final class HuntingSmoke {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Stack extends ItemStack {
        private final Material material;
        Stack(Material material){this.material=material;}
        @Override public Material getType(){return material;}
    }
    private static <T> T stub(Class<T> api,Function<String,Object> methods){
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(self,method,args)->switch(method.getName()){
            case "equals" -> self==args[0];case "hashCode" -> System.identityHashCode(self);case "toString" -> api.getSimpleName();
            default -> methods.apply(method.getName());
        }));
    }
    private static Object unexpected(String method){throw new AssertionError("Unexpected entity access: "+method);}
    private static DamageSource source(Entity actor,Entity direct){return stub(DamageSource.class,m->switch(m){case "getCausingEntity"->actor;case "getDirectEntity"->direct;case "getDamageType"->null;default->unexpected(m);});}
    private static EntityDeathEvent death(Entity actor,Entity direct,EntityDamageEvent.DamageCause cause){
        var last=new AtomicReference<EntityDamageEvent>();
        Zombie victim=stub(Zombie.class,m->switch(m){case "getType"->EntityType.ZOMBIE;case "getLastDamageCause"->last.get();default->unexpected(m);});
        var source=source(actor,direct);
        last.set(new EntityDamageByEntityEvent(direct,victim,cause,source,10));
        return new EntityDeathEvent(victim,source,new ArrayList<>());
    }
    private static <T extends Projectile> T shot(Class<T> api,Player owner,Material weapon){return stub(api,m->switch(m){case "getShooter"->owner;case "getWeapon","getItem"->new Stack(weapon);case "isShotAtAngle"->true;default->unexpected(m);});}
    public static void main(String[] args){
        AtomicReference<Material> held=new AtomicReference<>(Material.AIR);
        var inventory=stub(PlayerInventory.class,m->m.equals("getItemInMainHand")?new Stack(held.get()):unexpected(m));
        Player melee=stub(Player.class,m->m.equals("getInventory")?inventory:unexpected(m));
        Player remote=stub(Player.class,HuntingSmoke::unexpected);
        var attack=EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        var projectile=EntityDamageEvent.DamageCause.PROJECTILE;
        for(String tier:List.of("WOODEN","STONE","COPPER","IRON","GOLDEN","DIAMOND","NETHERITE"))
            for(String tool:List.of("SWORD","SPEAR","AXE","PICKAXE","SHOVEL","HOE"))
                ok(HuntingCredit.allowed(Material.valueOf(tier+"_"+tool)),"allow "+tier+"_"+tool);
        for(Material material:HuntingCredit.allowedItems()){
            held.set(material);ok(HuntingCredit.killer(death(melee,melee,attack),e->true)==melee,"melee whitelist "+material);
        }
        held.set(Material.AIR);ok(HuntingCredit.killer(death(melee,melee,attack),e->true)==melee,"empty hand");
        held.set(Material.STONE);ok(HuntingCredit.killer(death(melee,melee,attack),e->true)==null,"non-weapon item rejected");
        held.set(Material.DIAMOND_SWORD);ok(HuntingCredit.killer(death(melee,melee,EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK),e->true)==melee,"sweeping attack");
        ok(HuntingCredit.killer(death(melee,melee,attack),e->false)==null,"foreign melee inventory never accessed");
        for(Class<? extends AbstractArrow> api:List.of(Arrow.class,SpectralArrow.class))for(Material bow:List.of(Material.BOW,Material.CROSSBOW)){
            var arrow=shot(api,remote,bow);
            ok(HuntingCredit.killer(death(remote,arrow,projectile),e->e==arrow)==remote,"remote "+api.getSimpleName()+" with "+bow);
            ok(HuntingCredit.killer(death(remote,arrow,projectile),e->false)==null,"foreign projectile not inspected");
        }
        var trident=shot(Trident.class,remote,Material.TRIDENT);
        ok(HuntingCredit.killer(death(remote,trident,projectile),e->e==trident)==remote,"thrown trident without remote inventory access");
        var firework=shot(Firework.class,remote,Material.CROSSBOW);
        ok(HuntingCredit.killer(death(remote,firework,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION),e->e==firework)==remote,"crossbow firework");
        var potion=shot(ThrownPotion.class,remote,Material.SPLASH_POTION);
        ok(HuntingCredit.killer(death(remote,potion,EntityDamageEvent.DamageCause.MAGIC),e->e==potion)==remote,"direct splash potion");
        for(Class<? extends Projectile> api:List.of(Snowball.class,Egg.class,WindCharge.class)){
            var shot=shot(api,remote,Material.SNOWBALL);
            ok(HuntingCredit.killer(death(remote,shot,projectile),e->e==shot)==remote,"player projectile "+api.getSimpleName());
        }
        var arrow=shot(Arrow.class,remote,Material.BOW);
        ok(HuntingCredit.killer(death(null,arrow,projectile),e->true)==null,"no player attribution / dispenser");
        ok(HuntingCredit.killer(death(melee,arrow,projectile),e->true)==null,"different shooter");
        var artificial=shot(Arrow.class,remote,Material.AIR);
        ok(HuntingCredit.killer(death(remote,artificial,projectile),e->true)==null,"arrow without launched weapon proof");
        for(var cause:List.of(EntityDamageEvent.DamageCause.THORNS,EntityDamageEvent.DamageCause.FIRE_TICK,EntityDamageEvent.DamageCause.FALL,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION))
            ok(HuntingCredit.killer(death(melee,melee,cause),e->true)==null,"indirect death rejected: "+cause);
        var cancelled=death(remote,arrow,projectile);cancelled.setCancelled(true);
        ok(HuntingCredit.killer(cancelled,e->true)==null,"cancelled death");
        var cancelledHit=death(remote,arrow,projectile);cancelledHit.getEntity().getLastDamageCause().setCancelled(true);
        ok(HuntingCredit.killer(cancelledHit,e->true)==null,"cancelled damage");
        var zero=death(remote,arrow,projectile);zero.getEntity().getLastDamageCause().setDamage(0);
        ok(HuntingCredit.killer(zero,e->true)==null,"zero damage");
        var old=death(remote,arrow,projectile);
        var different=new EntityDeathEvent(old.getEntity(),source(melee,melee),new ArrayList<>());
        ok(HuntingCredit.killer(different,e->true)==null,"previous hit cannot override actual death source");
        System.out.println("HuntingSmoke: "+checks+" checks passed");
    }
}
