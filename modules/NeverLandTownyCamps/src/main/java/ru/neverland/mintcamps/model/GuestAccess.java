package ru.neverland.mintcamps.model;

import java.util.*;

/** Guest permissions deliberately exclude teleporting, fuel, construction and camp management. */
public record GuestAccess(String name,long grantedAt,long expiresAt,Set<Right> rights) {
    public enum Right {
        SLEEP("Сон"),WORKBENCH("Верстаки"),STORAGE("Склад");
        public final String label;Right(String label){this.label=label;}
    }
    public GuestAccess{
        Objects.requireNonNull(name);rights=Set.copyOf(rights);
        if(name.isBlank()||name.length()>64||grantedAt<1||expiresAt<=grantedAt||expiresAt-grantedAt>10080L*60000||rights.isEmpty())throw new IllegalArgumentException("Неверный срок или права гостя лагеря");
    }
    public boolean active(long now){return now>=grantedAt&&now<expiresAt;}
    public boolean allows(Right right,long now){return active(now)&&rights.contains(right);}
}
