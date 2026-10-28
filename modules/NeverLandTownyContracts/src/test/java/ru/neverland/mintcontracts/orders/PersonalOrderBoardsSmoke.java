package ru.neverland.mintcontracts.orders;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import ru.neverland.mintcontracts.orders.PublicOrders.Template;
import ru.neverland.mintcontracts.orders.PublicOrders.Type;

public final class PersonalOrderBoardsSmoke {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Time extends Clock {
        Instant now=Instant.parse("2026-10-28T20:59:59Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("personal-orders");var time=new Time();
        var weights=new EnumMap<OrderRarity,Integer>(OrderRarity.class);
        for(var r:OrderRarity.values())weights.put(r,r.defaultWeight);
        var catalog=new ArrayList<Template>();
        for(var type:Type.values())for(var rarity:OrderRarity.values())for(int i=0;i<8;i++)
            catalog.add(new Template(type.name().toLowerCase()+"_"+rarity.name().toLowerCase()+"_"+i,"Заказ "+i,type,switch(type){case DELIVERY->"WHEAT";case MINING->"IRON_ORE";case HUNTING->"ZOMBIE";case FISHING->"COD";},16,12000,3600000,rarity));
        var boards=new PersonalOrderBoards(root,catalog,18,weights,time,new Random(14));
        UUID alice=new UUID(1,1),bob=new UUID(2,2);
        var a=boards.snapshot(alice);var b=boards.snapshot(bob);
        ok(!a.templates().equals(b.templates()),"players have separate random draws");
        ok(a.templates().size()==18&&b.templates().size()==18,"configured size per player");
        ok(a.templates().stream().map(Template::type).distinct().count()==4,"all activities in personal board");
        byte[] saved=Files.readAllBytes(root.resolve(alice+".yml"));
        for(int i=0;i<20;i++)ok(boards.snapshot(alice).equals(a),"reopening does not reroll");
        ok(Arrays.equals(saved,Files.readAllBytes(root.resolve(alice+".yml"))),"reading another board does not rewrite Alice");
        var restart=new PersonalOrderBoards(root,catalog,18,weights,time,new Random(999));
        ok(restart.snapshot(alice).equals(a)&&restart.snapshot(bob).equals(b),"restart preserves exact individual terms");
        for(int i=0;i<260;i++)boards.snapshot(new UUID(10,i));
        ok(boards.snapshot(alice).equals(a),"cache eviction cannot reroll a board");
        var edited=new PersonalOrderBoards(root,List.of(catalog.get(0)),1,weights,time,new Random(8));
        ok(edited.snapshot(alice).equals(a),"catalog editing preserves today's terms for this player");
        time.now=time.now.plusSeconds(1);
        var next=restart.snapshot(alice);
        ok(next.day().equals(LocalDate.of(2026,10,29))&&!next.templates().equals(a.templates()),"personal draw refreshes at exact Moscow midnight");
        ok(edited.snapshot(bob).templates().equals(List.of(catalog.get(0))),"next day adopts edited catalog per player");
        time.now=time.now.minusSeconds(2);
        ok(restart.snapshot(alice).equals(next),"clock rollback cannot offer another daily draw");
        time.now=time.now.plusSeconds(2);
        Path legacy=root.resolve("orders-board.yml");Files.writeString(legacy,"schema: 1\nday: 2026-10-28\n");
        var legacyBytes=Files.readAllBytes(legacy);restart.snapshot(new UUID(20,1));
        ok(Arrays.equals(legacyBytes,Files.readAllBytes(legacy)),"legacy shared board is retained and never overwritten");
        UUID broken=new UUID(99,99);Path corrupt=root.resolve(broken+".yml");Files.writeString(corrupt,"schema: 999\n");
        try{restart.snapshot(broken);throw new AssertionError("corrupt personal draw must not reroll");}catch(java.io.IOException expected){checks++;}
        ok(Files.readString(corrupt).equals("schema: 999\n"),"corrupt board stays available for recovery");
        var blocked=root.resolve("not-directory");Files.writeString(blocked,"unchanged");
        var unavailable=new PersonalOrderBoards(blocked,catalog,18,weights,time,new Random(3));
        try{unavailable.snapshot(alice);throw new AssertionError("unsaved draw exposed");}catch(java.io.IOException expected){checks++;}
        System.out.println("PERSONAL_ORDER_BOARDS_SMOKE_PASS: "+checks+" checks");
    }
}
