package ru.neverland.townyseasons;

import java.nio.file.*;
import java.util.*;
import ru.neverland.townyseasons.markets.*;

public final class SeasonMarketsSmoke {
    static int checks;static final UUID WORLD=UUID.randomUUID();
    interface Work{void run()throws Exception;}
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void rejects(Work action,String why)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var spring=new CalendarWindow(WORLD,"world","CUSTOM",Season.SPRING,1,1,7,true);
        check(!spring.fairOpen(2,true)&&spring.next()==Season.SUMMER,"preparation follows existing next season while early fair remains closed");
        var near=new CalendarWindow(WORLD,"world","CUSTOM",Season.SPRING,1,6,7,true);check(near.fairOpen(2,true)&&near.key().equals(spring.key()),"one existing season has one fair identity across days");
        check(!new CalendarWindow(WORLD,"world","CUSTOM",Season.SPRING,2,6,7,true).key().equals(spring.key()),"later numbered calendar year cannot alias an earlier fair");
        var manual=new CalendarWindow(WORLD,"world","MANUAL",Season.WINTER,0,0,7,true);check(manual.fairOpen(2,true)&&!manual.fairOpen(2,false)&&manual.next()==Season.SPRING,"undated source uses explicit policy without inventing year/day");
        check(!manual.key().equals(new CalendarWindow(WORLD,"world","REALISTIC_SEASONS",Season.WINTER,0,0,7,true).key()),"changing calendar source invalidates old proposal key");
        var disabled=new CalendarWindow(WORLD,"world","DISABLED",null,0,0,7,false);check(!disabled.fairOpen(2,true),"disabled or nonnormal world never opens a fair");rejects(disabled::key,"disabled world cannot create accepted terms");
        rejects(()->new CalendarWindow(WORLD,"world","CUSTOM",Season.WINTER,0,0,7,true),"numbered sources require full existing calendar date");
        check(manual.closedReason(2,false).contains("без даты")&&spring.closedReason(2,true).contains("6-го"),"unavailable actions have understandable calendar explanations");
        var view=new HashMap<String,Object>();view.put("world",WORLD);view.put("worldName","world");view.put("source","MINECRAFT");view.put("season","AUTUMN");view.put("year",3L);view.put("day",7);view.put("daysPerSeason",7);view.put("enabled",true);check(CalendarWindow.read(view).season()==Season.AUTUMN&&CalendarWindow.read(view).fairOpen(2,true),"view reads sole existing calendar without a private timer");
        Path file=Files.createTempFile("season-fair-catalog-",".yml");try(var input=SeasonMarketsSmoke.class.getClassLoader().getResourceAsStream("season-markets.yml")){if(input==null)throw new AssertionError("missing resource");Files.copy(input,file,StandardCopyOption.REPLACE_EXISTING);}var catalog=SeasonMarketsCatalog.load(file);Files.delete(file);
        check(catalog.plans().size()==4&&catalog.exchanges().size()==8&&catalog.reserveHours()==24&&catalog.offerHours()==2,"bounded defaults include four preparations and eight actual barter templates");
        var food=catalog.exchanges().get("food_for_industry");check(food.give().equals(Map.of("farm_provisions",2L))&&food.receive().equals(Map.of("industrial_parts",1L)),"both exact sides belong to the same immutable proposal template");
        rejects(()->new SeasonMarketsCatalog.Exchange("x","Same goods",Map.of("farm_provisions",1L),Map.of("farm_provisions",2L)),"self exchange cannot conceal duplicated quantities");
        rejects(()->new SeasonMarketsCatalog.Plan("Too much",Map.of("farm_provisions",Long.MAX_VALUE)),"unbounded stock request rejected");
        rejects(()->new SeasonMarketsCatalog.Plan("Empty",Map.of()),"empty preparation cannot pretend to be funded");
        rejects(()->new SeasonMarketsCatalog.Plan("Bad quantity",Map.of("farm_provisions",0L)),"zero quantity cannot become a free positive stock change");
        var mutable=new HashMap<String,Long>();mutable.put("farm_provisions",2L);var frozen=new SeasonMarketsCatalog.Plan("Stored quote",mutable);mutable.put("farm_provisions",9L);check(frozen.goods().get("farm_provisions")==2,"quoted plan never observes later mutable template edits");
        System.out.println("SeasonMarketsSmoke PASS: "+checks+" assertions");
    }
}
