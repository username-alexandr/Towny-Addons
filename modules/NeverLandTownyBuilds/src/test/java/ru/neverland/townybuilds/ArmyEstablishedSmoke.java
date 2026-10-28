package ru.neverland.townybuilds;

import java.util.*;
import org.bukkit.block.BlockFace;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.construction.ConstructionSite;

public final class ArmyEstablishedSmoke {
    private static int checks;
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var town=new TownData(UUID.randomUUID(),54);
        ok(!town.armyEstablished(),"new town protected");
        town.setLevel("town_hall",5);ok(!town.armyEstablished(),"town hall does not end protection");
        town.setConstructionSite(new ConstructionSite("army",UUID.randomUUID(),0,64,0,BlockFace.NORTH,0,1,1,true));
        ok(!town.armyEstablished(),"blueprint and unfinished army do not end protection");
        town.removeConstructionSite("army");ok(!town.armyEstablished(),"cancelled first army remains protected");
        town.setLevel("army",1);ok(town.armyEstablished(),"completed army ends protection");
        town.setLevel("army",0);town.removeConstructionSite("army");ok(town.armyEstablished(),"demolition cannot restore immunity");
        town.loadArmyEstablished(false);ok(town.armyEstablished(),"loading an older false flag cannot reset established history");
        var restored=new TownData(UUID.randomUUID(),54);restored.loadArmyEstablished(town.armyEstablished());
        ok(restored.armyEstablished(),"persisted history survives with no current army level");
        var old=new TownData(UUID.randomUUID(),54);old.loadLevels(Map.of("army",2));old.setLevel("army",0);
        ok(old.armyEstablished(),"old completed levels migrate before possible demolition");
        var site=new TownData(UUID.randomUUID(),54);site.loadConstructionSites(Map.of("army",new ConstructionSite("army",UUID.randomUUID(),0,64,0,BlockFace.NORTH,1,2,2,true)));site.removeConstructionSite("army");
        ok(site.armyEstablished(),"existing completed stage migrating during upgrade is enough");
        System.out.println("ARMY_ESTABLISHED_SMOKE_PASS: "+checks+" checks");
    }
}
