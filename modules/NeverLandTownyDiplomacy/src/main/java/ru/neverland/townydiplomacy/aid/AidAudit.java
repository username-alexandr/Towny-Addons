package ru.neverland.townydiplomacy.aid;

import java.util.*;
import ru.neverland.core.AuditRecord;

/** Immutable receipts captured together with the state change, before any disk operation. */
public final class AidAudit {
    public static final String MODULE="NeverLandTownyDiplomacy",KIND="DIPLOMACY_AID";
    private AidAudit(){}
    public static AidState capture(AidState before,AidState next,long recordedAt){
        var records=new LinkedHashMap<>(before.outbox());var history=new HashSet<UUID>();before.history().forEach(h->history.add(h.id()));
        for(var h:next.history())if(!history.contains(h.id())){var p=next.pacts().get(h.pact());String outcome=switch(h.action()){case "OFFER"->"PROPOSED";case "ACCEPT","SIGNED"->"SIGNING";case "ACTIVATED"->"CONFIRMED";case "REJECT"->"REJECTED";case "END"->"CANCELLED";default->"UNASSESSED";};
            var receipt=record(p.id(),"decision:"+h.id(),h.at(),outcome,actor(h.actor(),p),town(p.first()),town(p.second()),"Соглашение "+p.id()+"; союз "+p.alliance()+"; действие "+h.action()+"; мэры "+p.firstMayor()+" / "+p.secondMayor()+"; подписи "+p.proposer()+" / "+p.accepter()+"; окончание "+p.endsAt()+"; ответ "+p.responseMillis()+" мс; цель "+p.requiredMilliDamage()+" тысячных урона; "+p.note());records.put(receipt.id(),receipt);
        }
        for(var c:next.calls().values()){var old=before.calls().get(c.id());if(c.equals(old))continue;
            var priorProof=new HashSet<UUID>();if(old!=null)old.proof().forEach(f->priorProof.add(f.id()));for(var fact:c.proof())if(!priorProof.contains(fact.id())){var receipt=record(c.id(),"evidence:"+fact.id(),fact.at(),"PROGRESS",player(fact.actor(),fact.actorTown()),town(c.helper()),town(c.beneficiary()),"Вызов "+c.id()+"; соглашение "+c.pact()+"; квитанция "+fact.id()+"; помощь игрока "+fact.actor()+" против "+fact.victim()+" города "+fact.victimTown()+"; место "+fact.world()+" "+fact.x()+" "+fact.y()+" "+fact.z()+"; урон "+fact.milliDamage()+" тысячных");records.put(receipt.id(),receipt);}
            if(old!=null&&old.status()==c.status())continue;long at=recordedAt;AuditRecord.Party person=AuditRecord.Party.system();String step="status:"+c.status();
            if(old==null){at=c.opened();person=player(c.attack().actor(),c.enemy());step="open";}
            else if(c.proof().size()>old.proof().size()){var fact=c.proof().get(c.proof().size()-1);at=fact.at();person=player(fact.actor(),fact.actorTown());step="proof:"+fact.id();}
            String outcome=switch(c.status()){case OPEN->old==null?"REQUESTED":"PROGRESS";case FULFILLED->"COMPLETED";case UNANSWERED->"UNCONFIRMED";case UNASSESSED->"UNASSESSED";};
            var receipt=record(c.id(),step,at,outcome,person,town(c.helper()),town(c.beneficiary()),"Вызов "+c.id()+"; соглашение "+c.pact()+"; противник "+c.enemy()+"; нападение "+c.attack().id()+"; нападавший "+c.attack().actor()+"; потерпевший "+c.attack().victim()+"; место "+c.attack().world()+" "+c.attack().x()+" "+c.attack().y()+" "+c.attack().z()+"; срок "+c.deadline()+"; помощь "+c.confirmedMilliDamage()+" / "+c.requiredMilliDamage()+" тысячных урона; квитанции "+c.proof().stream().map(f->f.id().toString()).toList()+"; "+c.note());records.put(receipt.id(),receipt);
        }
        return next.withOutbox(records);
    }
    private static AuditRecord record(UUID operation,String step,long at,String outcome,AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,String details){return new AuditRecord(AuditRecord.id(MODULE,operation.toString(),step),at,MODULE,KIND,outcome,operation.toString(),actor,from,to,"",0,"",details);}
    private static AuditRecord.Party town(UUID id){return new AuditRecord.Party("TOWN",id.toString(),"Город "+id.toString().substring(0,8),id.toString());}
    private static AuditRecord.Party player(UUID id,UUID town){return new AuditRecord.Party("PLAYER",id.toString(),"Игрок "+id.toString().substring(0,8),town==null?"":town.toString());}
    private static AuditRecord.Party actor(String id,AidPact pact){try{UUID uuid=UUID.fromString(id);return player(uuid,uuid.equals(pact.secondMayor())?pact.second():uuid.equals(pact.firstMayor())?pact.first():null);}catch(IllegalArgumentException nonPlayer){return id.startsWith("CONSOLE:")?new AuditRecord.Party("CONSOLE","",id.substring(8),""):AuditRecord.Party.system();}}
}
