package ru.neverland.townyresearch.licenses;
import java.util.*;import ru.neverland.core.AuditRecord;
/** Captured at the decision, never resolved from Towny on an audit worker. */
public record LicenseActor(String kind,String id,String name,String town){
 public LicenseActor{if(!Set.of("PLAYER","SYSTEM").contains(kind)||id==null||name==null||name.length()>128||town==null)throw new IllegalArgumentException("Неверный инициатор лицензии");if(kind.equals("PLAYER"))UUID.fromString(id);else if(!id.isEmpty())throw new IllegalArgumentException("SYSTEM id must be empty");if(!town.isEmpty())UUID.fromString(town);}
 public static LicenseActor system(){return new LicenseActor("SYSTEM","","NeverLandTownyResearch","");}
 public static LicenseActor player(UUID id,String name,UUID town){return new LicenseActor("PLAYER",id.toString(),name,town==null?"":town.toString());}
 public AuditRecord.Party party(){return new AuditRecord.Party(kind,id,name,town);}
}
