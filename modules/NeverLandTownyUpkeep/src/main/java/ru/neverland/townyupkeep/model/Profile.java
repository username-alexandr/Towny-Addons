package ru.neverland.townyupkeep.model;
public record Profile(String id,String name,String icon,boolean enabled,int priority,Cost cost) {
    public Profile {if(id==null||!id.matches("[a-z0-9_-]{1,64}")||name==null||name.isBlank()||icon==null||icon.isBlank()||priority<0||priority>100||cost==null)throw new IllegalArgumentException("Неверный профиль обслуживания");}
}
