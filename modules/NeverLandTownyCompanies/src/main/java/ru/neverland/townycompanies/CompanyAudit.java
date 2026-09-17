package ru.neverland.townycompanies;
import ru.neverland.core.*;
import org.bukkit.plugin.Plugin;
import static ru.neverland.townycompanies.CompanyData.*;

final class CompanyAudit {
    private CompanyAudit(){}
    static void changed(Plugin plugin,CompanyLedger.State before,CompanyLedger.State after){try{
        for(var p:after.payments().values())if(!p.equals(before.payments().get(p.id()))){var c=after.companies().get(p.company());var company=new AuditRecord.Party("COMPANY",c.id().toString(),c.name(),c.town().toString());boolean incoming=p.purpose()==Purpose.DEPOSIT;boolean player=p.purpose()==Purpose.DEPOSIT||p.purpose()==Purpose.WITHDRAW;var account=player?AuditTrail.player(p.account()):AuditTrail.town(p.account());
            AuditTrail.record(plugin,p.phase().name(),p.id().toString(),"COMPANY_PAYMENT",p.phase().name(),player?account:AuditRecord.Party.system(),incoming?account:company,incoming?company:account,"",0,AuditTrail.cents(p.amount()),"purpose="+p.purpose()+"; created="+p.created());}
        for(var r:after.receipts().values())if(!r.equals(before.receipts().get(r.contract()))){var c=after.companies().get(r.company());AuditTrail.record(plugin,"settlement",r.contract().toString(),"COMPANY_CONTRACT","COMPLETED",AuditRecord.Party.system(),AuditTrail.town(r.town()),new AuditRecord.Party("COMPANY",c.id().toString(),c.name(),c.town().toString()),"",0,AuditTrail.cents(r.payout()),"refund="+AuditTrail.cents(r.refund())+"; contract="+r.contract());}
    }catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: компании",ex);}}
}
