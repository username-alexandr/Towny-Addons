package ru.neverland.townyresearch.licenses;
import org.bukkit.configuration.ConfigurationSection;
public record LicenseSettings(boolean enabled,int offerSeconds,int maximumOffers){
 public LicenseSettings{if(offerSeconds<60||offerSeconds>604800||maximumOffers<1||maximumOffers>64)throw new IllegalArgumentException("Лицензии: срок предложения 60..604800 секунд, предложений 1..64");}
 public static LicenseSettings load(ConfigurationSection config){Object enabled=config.get("licenses.enabled",true);if(!(enabled instanceof Boolean b))throw new IllegalArgumentException("licenses.enabled: true/false");return new LicenseSettings(b,integer(config,"licenses.offer-seconds",86400),integer(config,"licenses.max-open-offers",16));}
 private static int integer(ConfigurationSection c,String key,int fallback){return new java.math.BigDecimal(String.valueOf(c.get(key,fallback))).intValueExact();}
}
