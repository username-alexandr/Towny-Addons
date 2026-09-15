package ru.neverland.townysieges;

import java.util.*;

/** Pure geometry and balance. No Bukkit objects or world mutation. */
public final class SiegeRules {
    private SiegeRules() { }
    public enum Fort {
        WALL("fortress_wall", "Крепостная стена"), GATE("fortress_gate", "Крепостные ворота"),
        MOAT("city_moat", "Городской ров"), TOWER("watchtower", "Дозорная башня"),
        KEEP("watch_fortress", "Сторожевая крепость"), PORT("port_fort", "Портовый форт");
        public final String project, title;
        Fort(String project, String title) { this.project = project; this.title = title; }
    }
    public record Point(UUID world, double x, double y, double z) {
        public Point { Objects.requireNonNull(world); if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Неверные координаты"); }
    }
    public record Line(double x1,double z1,double x2,double z2,double width) {
        public boolean near(Point p) {
            double dx=x2-x1,dz=z2-z1,length=dx*dx+dz*dz;
            double t=length==0?0:Math.max(0,Math.min(1,((p.x-x1)*dx+(p.z-z1)*dz)/length));
            double x=p.x-x1-t*dx,z=p.z-z1-t*dz;return x*x+z*z<=width*width;
        }
    }
    public record Zone(UUID world, double x1, double y1, double z1, double x2, double y2, double z2,Line line) {
        public Zone(UUID world,double x1,double y1,double z1,double x2,double y2,double z2){this(world,x1,y1,z1,x2,y2,z2,null);}
        public static Zone along(UUID world,double x1,double y1,double z1,double x2,double y2,double z2,double width,double height){
            return new Zone(world,Math.min(x1,x2)-width,Math.min(y1,y2)-8,Math.min(z1,z2)-width,Math.max(x1,x2)+width,Math.max(y1,y2)+height,Math.max(z1,z2)+width,new Line(x1,z1,x2,z2,width));
        }
        public Zone {
            Objects.requireNonNull(world);
            if (!Double.isFinite(x1+y1+z1+x2+y2+z2) || x1>x2 || y1>y2 || z1>z2) throw new IllegalArgumentException("Неверная зона обороны");
        }
        public boolean contains(Point p) { return world.equals(p.world) && p.x>=x1 && p.x<=x2 && p.y>=y1 && p.y<=y2 && p.z>=z1 && p.z<=z2 && (line==null||line.near(p)); }
        /** Swept segment test prevents jumping over a thin gate between movement packets. Exit is always possible. */
        public boolean enters(Point from, Point to) {
            if (!world.equals(to.world) || contains(from)) return false;
            if (!world.equals(from.world)) return contains(to);
            double low=0, high=1;
            double[] a={from.x,from.y,from.z}, b={to.x,to.y,to.z}, min={x1,y1,z1}, max={x2,y2,z2};
            for (int i=0;i<3;i++) {
                double d=b[i]-a[i];
                if (Math.abs(d)<1e-10) { if(a[i]<min[i] || a[i]>max[i]) return false; continue; }
                double l=(min[i]-a[i])/d, h=(max[i]-a[i])/d;
                low=Math.max(low,Math.min(l,h)); high=Math.min(high,Math.max(l,h));
                if (low>high) return false;
            }
            return high>=0 && low<=1;
        }
    }
    public static double reduction(int wall, int tower, double army, double wallRate, double towerRate, double cap) {
        return Math.min(cap, Math.max(0,wall)*wallRate + Math.max(0,tower)*towerRate + (Double.isFinite(army)?Math.max(0,Math.min(.2,army)):0));
    }
    public static double moat(int level, double perLevel, double cap) { return Math.min(cap,Math.max(0,level)*perLevel); }
}
