package ru.neverland.townyjobs;
import java.util.*;
import java.nio.file.*;
import org.bukkit.Material;
import ru.neverland.integration.JobsEffects;
import static ru.neverland.townyjobs.WorkPolicy.*;
/** Pure eligibility, bounds and durable-file failure tests; native event wiring has a separate opt-in probe. */
public final class JobsSmoke {
    private static int checks;
    private static void ok(boolean value,String msg){checks++;if(!value)throw new AssertionError(msg);}
    private static void near(double expected,double actual,String msg){ok(Math.abs(expected-actual)<1e-9,msg+": "+actual);}
    private interface Attempt{void run()throws Exception;}
    private static void reject(Attempt action,String msg)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        UUID town=UUID.randomUUID(),world=UUID.randomUUID(),actor=UUID.randomUUID();
        Map<Profession,JobsSettings.Profile> profiles=new EnumMap<>(Profession.class);
        for(Profession p:Profession.values())profiles.put(p,new JobsSettings.Profile(p.id(),Material.PAPER,true,Set.of(p.id()),List.of(.02,.035,.05,.075,.10),List.of()));
        var settings=new JobsSettings(10,6,30,90,8,16,2,.30,List.of(0,30,120,360,900),profiles,Map.of());
        var site=new Site(world,16,16,24,24,64,5,true,true);var person=new Presence(town,world,20,65,20,true,true);long now=1_000_000;
        var active=new Shift(now-30000,now);ok(seats(site,settings)==10&&seats(null,settings)==0,"capacity by completed level");
        for(Profession p:Profession.values()){
            Career c=Career.empty().choose(p,1).work(town,p.id(),2);
            ok(status(c,site,person,active,true,settings,now).equals("На смене"),"active role "+p);
            for(int i=0;i<180;i++)c=c.earn(10);ok(settings.level(c)==2,"30 active minutes promotes "+p);
            Career switched=c.choose(Profession.values()[(p.ordinal()+1)%7],now);
            ok(switched.town()==null&&switched.seconds().get(p)==1800&&settings.level(switched)==1,"role switch preserves separate experience and releases seat");
            ok(settings.level(switched.choose(p,now+1))==2,"return to previous career");
            Career master=new Career(p,Map.of(p,54000L),town,p.id(),2,1,1);ok(settings.level(master)==5,"five levels "+p);
            reject(()->master.earn(61),"no unbounded offline catchup");
        }
        Career c=Career.empty().choose(Profession.FARMER,1).work(town,"farmer",2);
        ok(!status(c,site,person,null,true,settings,now).equals("На смене"),"restart has no shift");
        ok(!status(c,site,person,new Shift(now-29999,now),true,settings,now).equals("На смене"),"warmup boundary");
        ok(!status(c,site,person,new Shift(now-100000,now-90001),true,settings,now).equals("На смене"),"activity expiration");
        ok(!status(c,site,person,new Shift(now+1,now),true,settings,now).equals("На смене"),"clock rollback");
        ok(!status(c,site,person,active,false,settings,now).equals("На смене"),"no seat after downgrade");
        for(Presence invalid:Arrays.asList(null,new Presence(town,world,20,64,20,false,true),new Presence(town,world,20,64,20,true,false),new Presence(UUID.randomUUID(),world,20,64,20,true,true),new Presence(town,UUID.randomUUID(),20,64,20,true,true),new Presence(town,world,100,64,20,true,true),new Presence(town,world,20,200,20,true,true),new Presence(town,world,Double.NaN,64,20,true,true)))ok(!status(c,site,invalid,active,true,settings,now).equals("На смене"),"invalid presence");
        for(Site invalid:Arrays.asList(null,new Site(world,16,16,24,24,64,0,true,true),new Site(world,16,16,24,24,64,5,false,true),new Site(world,16,16,24,24,64,5,true,false)))ok(!status(c,invalid,person,active,true,settings,now).equals("На смене"),"missing/unowned/inactive building");
        ok(!status(c.work(town,"merchant",3),site,person,active,true,settings,now).equals("На смене"),"wrong profession building");
        UUID older=new UUID(0,1),newer=new UUID(0,2);var rows=Map.of(older,c,newer,c);ok(slot(older,c,rows,1)&&!slot(newer,c,rows,1),"deterministic tie after downgrade");
        near(.3,sum(Collections.nCopies(20,.1),.3),"stacking cap");near(.1,sum(List.of(Double.NaN,Double.POSITIVE_INFINITY,-1.,.1),.3),"nonfinite worker bonus");
        near(3,JobsEffects.multiplier(2.9,.5),"combined multiplier cap");near(1,JobsEffects.multiplier(Double.NaN,Double.NaN),"nonfinite integrations neutral");near(.5,JobsEffects.research(.4,.3),"research combined cap");near(1.53,JobsEffects.multiplier(1.5,.02),"district and profession compound once");
        Path dir=Files.createTempDirectory("towny-jobs-smoke"),file=dir.resolve("workers.yml");var repo=new JobsRepository(file);repo.load();repo.put(actor,c.earn(10));
        var restarted=new JobsRepository(file);restarted.load();ok(restarted.get(actor).equals(c.earn(10)),"assignment and XP survive restart");ok(restarted.get(actor).revision()==c.revision(),"XP does not invalidate career revision");
        var before=repo.all();Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("blocker"),"disk failure fixture");reject(()->repo.put(actor,c.leave()),"failed atomic move");ok(!repo.writable()&&repo.all().equals(before),"write failure preserves published state and stops mutations");reject(()->repo.put(actor,c.leave()),"fail closed after failed save");
        Path malformed=dir.resolve("broken.yml");Files.writeString(malformed,"schema: 1\nworkers: damaged\n");var broken=new JobsRepository(malformed);reject(broken::load,"corrupt file cannot reset data");ok(!broken.writable(),"corrupt database not writable");
        reject(()->new Career(null,Map.of(),town,"farmer",0,0,0),"assignment requires profession");reject(()->new Career(Profession.FARMER,Map.of(Profession.FARMER,-1L),null,"",0,0,0),"negative experience");reject(()->JobsSettings.integer(1.5),"fractional integers rejected");
        System.out.println("JOBS_SMOKE_PASS: "+checks+" checks");
    }
}
