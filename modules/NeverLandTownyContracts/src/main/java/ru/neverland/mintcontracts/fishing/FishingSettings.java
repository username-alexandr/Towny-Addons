package ru.neverland.mintcontracts.fishing;

import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.SafeYaml;

public record FishingSettings(boolean enabled,boolean withoutTask,boolean requireOpenWater,int radius,int capacity,long recoveryMillis,long leaseMillis,
                              int minimumSurface,int minimumSpan,int scanRadius,int minimumDepth) {
    public FishingSettings(boolean enabled,boolean withoutTask,boolean requireOpenWater,int radius,int capacity,long recoveryMillis,long leaseMillis) {
        this(enabled,withoutTask,requireOpenWater,radius,capacity,recoveryMillis,leaseMillis,25,5,8,2);
    }
    public FishingSettings(boolean enabled,boolean withoutTask,boolean requireOpenWater,int radius,int capacity,long recoveryMillis,long leaseMillis,
                           int minimumSurface,int minimumSpan,int scanRadius) {
        this(enabled,withoutTask,requireOpenWater,radius,capacity,recoveryMillis,leaseMillis,minimumSurface,minimumSpan,scanRadius,2);
    }
    public FishingSettings {
        if(radius<8||radius>128||capacity<1||capacity>10000||recoveryMillis<60000||recoveryMillis>86400000||leaseMillis<1000||leaseMillis>60000)
            throw new IllegalArgumentException("Некорректные настройки водоёма и восстановления рыбы");
        if(minimumDepth<2||minimumDepth>8||scanRadius<3||scanRadius>16||minimumSpan<3||minimumSpan>scanRadius*2+1||minimumSurface<9
                ||minimumSurface>(scanRadius*2+1)*(scanRadius*2+1))
            throw new IllegalArgumentException("Некорректный минимальный размер водоёма");
    }
    public static FishingSettings read(ConfigurationSection c){
        return new FishingSettings(SafeYaml.booleanValue(c,"enabled",true),SafeYaml.booleanValue(c,"apply-without-task",false),
            SafeYaml.booleanValue(c,"habitat.require-open-water",true),SafeYaml.intValue(c,"stock.radius",24),SafeYaml.intValue(c,"stock.capacity",20),
            Math.multiplyExact(SafeYaml.longValue(c,"stock.recovery-seconds",1800),1000),Math.multiplyExact(SafeYaml.longValue(c,"reservation-seconds",15),1000),
            SafeYaml.intValue(c,"habitat.minimum-water-blocks",25),SafeYaml.intValue(c,"habitat.minimum-span",5),SafeYaml.intValue(c,"habitat.scan-radius",8),
            SafeYaml.intValue(c,"habitat.minimum-depth",2));
    }
}
