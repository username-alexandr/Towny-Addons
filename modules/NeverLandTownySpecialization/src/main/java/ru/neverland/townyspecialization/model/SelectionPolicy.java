package ru.neverland.townyspecialization.model;
import ru.neverland.townyspecialization.config.SpecializationSettings;
public final class SelectionPolicy {
    private SelectionPolicy(){}
    public static CityChoice choose(CityChoice current,Specialization target,int townLevel,int hallLevel,SpecializationSettings settings,long now,long expectedRevision,boolean admin){
        if(now<=0||expectedRevision!=current.revision())throw new IllegalArgumentException("Выбор изменился. Откройте меню заново.");
        if(!target.enabled())throw new IllegalArgumentException("Направление отключено в настройках");if(current.specialization().equals(target.id()))throw new IllegalArgumentException("Это направление уже выбрано");
        if(!admin){if(townLevel<settings.minimumTownLevel())throw new IllegalArgumentException("Нужен уровень города Towny "+settings.minimumTownLevel());if(hallLevel<settings.minimumHallLevel())throw new IllegalArgumentException("Нужна ратуша уровня "+settings.minimumHallLevel());if(now<current.nextChangeAt())throw new IllegalArgumentException("Срок ожидания смены ещё не истёк");}
        return new CityChoice(target.id(),now,Math.addExact(now,settings.cooldownMillis()),Math.addExact(current.revision(),1));
    }
}
