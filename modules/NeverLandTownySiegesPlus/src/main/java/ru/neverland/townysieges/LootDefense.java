package ru.neverland.townysieges;
import org.bukkit.configuration.ConfigurationSection;
public record LootDefense(int perLevelBps,int maximumBps,boolean garrison) {
    public record Quote(int levels,int reductionBps,LootSettings effective,String description){}
    public static LootDefense load(ConfigurationSection y){return new LootDefense(LootSettings.percent(y,"loot.defense.reduction-percent-per-level"),LootSettings.percent(y,"loot.defense.maximum-reduction-percent"),SiegeSettings.bool(y,"loot.defense.include-garrison"));}
    public Quote quote(int levels,double army,LootSettings base){
        if(levels<0||levels>30||!Double.isFinite(army)||army<0||army>.200001)throw new IllegalArgumentException("Неверный уровень защиты города");
        int reduction=(int)Math.min(maximumBps,(long)levels*perLevelBps+(garrison?Math.round(Math.min(.2,army)*10000):0));
        var effective=new LootSettings(base.enabled(),(int)LootSettings.share(base.moneyBps(),10000-reduction),(int)LootSettings.share(base.resourcesBps(),10000-reduction));
        return new Quote(levels,reduction,effective,"Защита: "+levels+" уровней; укрепления "+((long)levels*perLevelBps)/100.0+"%; гарнизон "+(garrison?Math.round(Math.min(.2,army)*10000)/100.0:0)+"%; снижение добычи "+reduction/100.0+"%; казна "+base.moneyBps()/100.0+"% → "+effective.moneyBps()/100.0+"%; склад "+base.resourcesBps()/100.0+"% → "+effective.resourcesBps()/100.0+"%");
    }
}
