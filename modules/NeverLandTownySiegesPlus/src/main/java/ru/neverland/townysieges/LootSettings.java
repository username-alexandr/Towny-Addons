package ru.neverland.townysieges;
import org.bukkit.configuration.ConfigurationSection;
public record LootSettings(boolean enabled,int moneyBps,int resourcesBps) {
    public static LootSettings load(ConfigurationSection y){return new LootSettings(SiegeSettings.bool(y,"loot.enabled"),percent(y,"loot.money-percent"),percent(y,"loot.resources-percent"));}
    static int percent(ConfigurationSection y,String key){Object raw=y.get(key);if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>100||Math.abs(n.doubleValue()*100-Math.rint(n.doubleValue()*100))>.000001)throw new IllegalArgumentException("Нужен процент 0..100 с точностью до 0.01: "+key);return (int)Math.round(n.doubleValue()*100);}
    public static long share(long cents,int bps){if(cents<0||bps<0||bps>10000)throw new IllegalArgumentException("Неверная доля");return java.math.BigInteger.valueOf(cents).multiply(java.math.BigInteger.valueOf(bps)).divide(java.math.BigInteger.valueOf(10000)).longValueExact();}
}
