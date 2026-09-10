package ru.neverland.townydistricts.model;
import java.util.*;
public record District(UUID town,String id,String name,DistrictType type,Set<Cell> cells) {
    public District {
        Objects.requireNonNull(town);Objects.requireNonNull(type);
        if(id==null||!id.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("ID: 1–32 латинские буквы, цифры, _ или -");
        if(name==null||name.isBlank()||name.length()>48||name.chars().anyMatch(c->Character.isISOControl(c)||c=='§'||c=='&'))throw new IllegalArgumentException("Название: 1–48 символов без кодов цвета");
        cells=Set.copyOf(cells);
        if(cells.isEmpty()||cells.size()>4096)throw new IllegalArgumentException("Некорректная площадь района");
        if(cells.stream().map(Cell::world).distinct().count()!=1)throw new IllegalArgumentException("Район не может занимать несколько миров");
    }
    public District withCells(Set<Cell> value){return new District(town,id,name,type,value);}
}
