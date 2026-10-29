package ru.neverland.townyideologies.model;

import java.util.UUID;

public record TownIdeology(UUID townId, String townName, String ideologyId, int level, long selectedAt, PolicyTerms terms) {
    public TownIdeology(UUID townId,String townName,String ideologyId,int level,long selectedAt){this(townId,townName,ideologyId,level,selectedAt,null);}
    public TownIdeology {
        if(townId==null||townName==null||townName.isBlank()||ideologyId==null||!ideologyId.matches("[a-z][a-z0-9_]{0,63}")||level<1||level>5||selectedAt<0)throw new IllegalArgumentException("Неверная сохранённая идеология");
    }
    public TownIdeology withLevel(int newLevel){return new TownIdeology(townId,townName,ideologyId,newLevel,selectedAt,terms);}
}
