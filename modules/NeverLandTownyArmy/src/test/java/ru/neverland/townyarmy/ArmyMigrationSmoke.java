package ru.neverland.townyarmy;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import static ru.neverland.townyarmy.ArmyModel.*;
public final class ArmyMigrationSmoke {
    private static final class Legacy implements ArmyMigration.Legacy {
        Map<UUID,UUID> roster=Map.of(UUID.randomUUID(),UUID.randomUUID());boolean retired,loseReply=true;int acknowledgements,reads;
        public Map<UUID,UUID> legacy(){reads++;return retired?Map.of():roster;}
        public void handoff(Map<UUID,UUID> expected)throws IOException{if(!retired&&!roster.equals(expected))throw new IOException("Roster changed");retired=true;acknowledgements++;if(loseReply){loseReply=false;throw new IOException("Retirement persisted but reply lost");}}
    }
    public static void main(String[] args)throws Exception{
        var dir=Files.createTempDirectory("army-migration");var file=dir.resolve("army.yml");var r=new ArmyRepository(file,100);r.load();var old=new Legacy();
        ArmyRepositorySmoke.denied(()->ArmyMigration.resume(r,old,1));ArmyRepositorySmoke.check(old.retired&&r.state().imported()&&!r.state().handoff()&&r.state().soldiers().size()==1);
        var restart=new ArmyRepository(file,100);restart.load();ArmyMigration.resume(restart,old,2);ArmyRepositorySmoke.check(restart.state().handoff()&&restart.state().soldiers().size()==1&&old.reads==1&&old.acknowledgements==2);
        UUID id=old.roster.keySet().iterator().next();var d=restart.draft();d.soldier(d.soldiers.get(id).status(Status.DISCHARGED));restart.commit(d.freeze());ArmyMigration.resume(restart,old,3);ArmyRepositorySmoke.check(restart.state().soldiers().get(id).status()==Status.DISCHARGED&&old.acknowledgements==2);
        var blocked=dir.resolve("blocked.yml");var failing=new ArmyRepository(blocked,100);failing.load();Files.createDirectory(blocked);Files.writeString(blocked.resolve("block"),"x");var untouched=new Legacy();ArmyRepositorySmoke.denied(()->ArmyMigration.resume(failing,untouched,1));ArmyRepositorySmoke.check(!untouched.retired&&untouched.acknowledgements==0&&!failing.state().imported());
        System.out.println("Army migration: durable import, lost handoff reply, restart, no roster resurrection and failed-save barrier PASS");
    }
}
