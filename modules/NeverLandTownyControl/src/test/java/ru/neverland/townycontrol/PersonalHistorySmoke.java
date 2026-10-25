package ru.neverland.townycontrol;
import java.nio.file.*;
import java.util.*;
import ru.neverland.core.*;

public final class PersonalHistorySmoke {
    static int checks;
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    static AuditRecord record(UUID actor,String kind,String details){
        String operation=UUID.randomUUID().toString();var player=new AuditRecord.Party("PLAYER",actor.toString(),"Игрок","city");
        return new AuditRecord(AuditRecord.id("test",operation,"one"),System.currentTimeMillis(),"NeverLandTownyBuilds",kind,"COMPLETED",operation,player,player,new AuditRecord.Party("TOWN","city","Город","city"),"",1,"12.34",details);
    }
    public static void main(String[] args)throws Exception{
        UUID mine=UUID.randomUUID(),other=UUID.randomUUID();var filter=PersonalHistory.filter(mine,PersonalHistory.Category.ALL);
        AuditRecord own=record(mine,"PUBLIC_ORDER_REWARD","secret admin note"),foreign=record(other,"PUBLIC_ORDER_REWARD","private"),admin=record(mine,"ADMIN_INCOME","secret"),bank=record(mine,"BANK_LEG","provider data"),contribution=record(mine,"BUILD_CONTRIBUTION","Ратуша • уровень 1");
        check(filter.test(own),"own reward visible");check(!filter.test(foreign),"same town does not grant other player's history");check(!filter.test(admin),"admin notes excluded even for actor");
        check(!filter.test(bank)&&PersonalHistory.filter(mine,PersonalHistory.Category.BANK).test(bank),"bank observations separated from receipts");
        check(PersonalHistory.filter(mine,PersonalHistory.Category.RESOURCES).test(contribution),"resource contribution visible");
        check(!PersonalHistory.filter(mine,PersonalHistory.Category.RESOURCES).test(own),"resource filter excludes money");
        check(PersonalHistory.lines(own,mine).stream().noneMatch(x->x.contains("secret")),"raw details not disclosed");
        check(PersonalHistory.lines(contribution,mine).stream().anyMatch(x->x.contains("Ратуша")),"safe building name retained");
        Path root=Files.createTempDirectory("personal-history-");var journal=new AuditJournal(root.resolve("NeverLandTownyBuilds/audit"));
        for(var record:List.of(own,foreign,admin,bank,contribution))journal.append(record);
        var page=AuditReader.page(root,filter,1,36);check(page.rows().size()==2,"reader filters before pagination");
        var restarted=new AuditJournal(journal.directory());restarted.load();check(AuditReader.page(root,filter,1,36).rows().size()==2,"personal history survives restart");
        System.out.println("PersonalHistorySmoke PASS "+checks+" checks");
    }
}
