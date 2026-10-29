package ru.neverland.townylogistics.model;
import java.util.UUID;
public record Position(UUID world,double x,double y,double z) {
    public Position {if(world==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000||Math.abs(y)>4096)throw new IllegalArgumentException("Некорректная точка");}
    public double distance(Position p){return RoutePlanner.distance(this,p);}
    public String encode(){return world+","+x+","+y+","+z;}
    public static Position decode(String value){var p=value.split(",");if(p.length!=4)throw new IllegalArgumentException("Точка маршрута");return new Position(UUID.fromString(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]));}
}
