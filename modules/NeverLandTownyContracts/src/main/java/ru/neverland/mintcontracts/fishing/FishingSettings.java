package ru.neverland.mintcontracts.fishing;

import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;

public record FishingSettings(boolean enabled,boolean withoutTask,boolean requireOpenWater,int radius,int capacity,long recoveryMillis,long leaseMillis) {
    public FishingSettings {
        if(radius<8||radius>128||capacity<1||capacity>10000||recoveryMillis<60000||recoveryMillis>86400000||leaseMillis<1000||leaseMillis>60000)
            throw new IllegalArgumentException("Некорректные настройки водоёма и восстановления рыбы");
    }
    public static FishingSettings read(ConfigurationSection c){
        return new FishingSettings(SafeYaml.booleanValue(c,"enabled",true),SafeYaml.booleanValue(c,"apply-without-task",false),
            SafeYaml.booleanValue(c,"habitat.require-open-water",true),SafeYaml.intValue(c,"stock.radius",24),SafeYaml.intValue(c,"stock.capacity",20),
            Math.multiplyExact(SafeYaml.longValue(c,"stock.recovery-seconds",1800),1000),Math.multiplyExact(SafeYaml.longValue(c,"reservation-seconds",15),1000));
    }
}
