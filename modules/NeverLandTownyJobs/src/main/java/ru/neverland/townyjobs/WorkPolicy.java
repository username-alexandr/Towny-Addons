package ru.neverland.townyjobs;
import java.util.*;
/** Bounds and eligibility are independent of Bukkit and exercised under failures/restart. */
public final class WorkPolicy {
    public record Site(UUID world,int minX,int minZ,int maxX,int maxZ,int y,int level,boolean owned,boolean active){
        public Site{if(world==null||minX>maxX||minZ>maxZ||(long)maxX-minX>1024||(long)maxZ-minZ>1024||level<0||level>5)throw new IllegalArgumentException("Неверное рабочее здание");}
    }
    public record Presence(UUID town,UUID world,double x,double y,double z,boolean online,boolean allowed){}
    public record Shift(long started,long activity){public Shift touch(long now){return new Shift(started,now);}}
    public static int seats(Site s,JobsSettings settings){return s==null?0:Math.min(20,s.level()*settings.seats());}
    public static boolean near(Site s,Presence p,JobsSettings settings){return s!=null&&p!=null&&s.world().equals(p.world())&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z())&&p.x()>=s.minX()-settings.radius()&&p.x()<s.maxX()+1d+settings.radius()&&p.z()>=s.minZ()-settings.radius()&&p.z()<s.maxZ()+1d+settings.radius()&&Math.abs(p.y()-s.y())<=settings.height();}
    public static String status(Career c,Site site,Presence p,Shift shift,boolean slot,JobsSettings settings,long now){
        if(c.profession()==null)return "Профессия не выбрана";var profile=settings.profiles().get(c.profession());if(profile==null||!profile.enabled())return "Профессия отключена";
        if(c.town()==null)return "Рабочее место не выбрано";if(!profile.buildings().contains(c.building()))return "Здание не подходит профессии";
        if(site==null||site.level()<1||!site.owned())return "Нет завершённого здания на территории города";if(!site.active())return "Здание не работает: проверьте содержание и энергию";
        if(!slot)return "Место недоступно после снижения уровня здания";if(p==null||!p.online())return "Игрок не в сети";if(!c.town().equals(p.town()))return "Игрок сменил город";
        if(!p.allowed())return "Нужны права на работу и режим выживания";if(!near(site,p,settings))return "Нужно подойти к рабочему зданию";if(shift==null)return "Смена не начата";
        if(now<shift.started()||now-shift.started()<settings.warmup()*1000L)return "Подготовка к смене";
        if(shift.activity()<=0||now<shift.activity()||now-shift.activity()>settings.activity()*1000L)return "Ожидается работа по профессии";return "На смене";
    }
    public static boolean slot(UUID actor,Career career,Map<UUID,Career> all,int seats){return all.entrySet().stream().filter(e->career.town()!=null&&career.town().equals(e.getValue().town())&&career.building().equals(e.getValue().building())).sorted(Comparator.<Map.Entry<UUID,Career>>comparingLong(e->e.getValue().assignedAt()).thenComparing(e->e.getKey().toString())).limit(seats).anyMatch(e->e.getKey().equals(actor));}
    public static double sum(Collection<Double> values,double cap){double sum=0;for(double v:values)if(Double.isFinite(v)&&v>0)sum+=Math.min(0.5,v);return Math.min(Math.max(0,Math.min(0.5,cap)),sum);}
}
