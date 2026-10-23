package ru.neverland.townydistricts.service;

/** Towny's world restrictions have priority over a local plot flag. */
public final class MobFarmPolicy {
    private MobFarmPolicy(){}
    public static String status(boolean usingTowny,boolean worldMobs,boolean forced,boolean plotMobs){
        if(!usingTowny)return "TOWNY_DISABLED";
        if(!worldMobs)return "WORLD_RESTRICTED";
        if(forced)return "FORCED_ALLOWED";
        return plotMobs?"ALLOWED":"PROTECTED";
    }
    public static void validate(boolean usingTowny,boolean worldMobs,boolean forced,boolean grouped,boolean allow){
        if(!usingTowny)throw new IllegalStateException("В этом мире городская защита отключена администратором");
        if(forced)throw new IllegalStateException("Администратор разрешил мобов во всём городе или мире. Отдельный участок не может изменить это правило");
        if(allow&&!worldMobs)throw new IllegalStateException("Настройки мира ограничивают появление мобов. Обратитесь к администратору");
        if(grouped)throw new IllegalStateException("Участок входит в группу Towny. Сначала отделите его от группы, чтобы не менять соседние участки");
    }
}
