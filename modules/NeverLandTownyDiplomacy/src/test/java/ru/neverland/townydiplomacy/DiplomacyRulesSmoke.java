package ru.neverland.townydiplomacy;

import java.util.*;
import static ru.neverland.townydiplomacy.Treaty.*;

public final class DiplomacyRulesSmoke {
    static final UUID A=UUID.randomUUID(),B=UUID.randomUUID(),C=UUID.randomUUID(),D=UUID.randomUUID();
    static final long NOW=System.currentTimeMillis();static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void deny(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}throw new AssertionError("Invalid treaty accepted");}
    static Treaty offer(TreatyType type,UUID a,UUID b){return new Treaty(UUID.randomUUID(),type,a,b,A,B,"actor","Terms",type.bilateral()?Phase.PENDING:Phase.ACTIVE,NOW,NOW+1000,100_000,type.bilateral()?0:NOW,type.bilateral()?0:NOW+100_000,0,1000,type==TreatyType.TRADE?2500:0,type==TreatyType.SANCTIONS?SanctionScope.ALL:SanctionScope.NONE);}
    static Treaty active(TreatyType type,UUID a,UUID b){var t=offer(type,a,b);return type.bilateral()?t.accept(NOW):t;}
    public static void main(String[] args){
        var nap=offer(TreatyType.NONAGGRESSION,A,B);
        check(!DiplomacyRules.hostileBlocked(List.of(nap),A,B,NOW),"proposal has no effect");
        deny(()->nap.accept(NOW+1000));var accepted=nap.accept(NOW+900);
        check(accepted.expires()==NOW+100_900,"duration begins at acceptance");deny(()->accepted.accept(NOW+901));
        var ending=accepted.terminate(NOW+950,accepted.noticePeriod());
        check(ending.active(NOW+1949)&&!ending.active(NOW+1950),"protection ends at notice boundary");
        check(DiplomacyRules.hostileBlocked(List.of(ending),B,A,NOW+1000),"notice is bilateral");deny(()->ending.terminate(NOW+1000,1000));
        check(!accepted.active(accepted.expires()),"natural expiry is exclusive");
        check(accepted.terminate(accepted.expires()-10,1000).noticeUntil()==accepted.expires(),"notice cannot extend expiry");
        var alliance=active(TreatyType.ALLIANCE,A,B);var bc=active(TreatyType.ALLIANCE,B,C);
        check(!DiplomacyRules.hostileBlocked(List.of(alliance,bc),A,C,NOW),"alliances are not transitively inherited");
        check(DiplomacyRules.defenders(List.of(alliance,bc),B,NOW).equals(Set.of(A,C)),"direct defensive allies");
        var embargo=active(TreatyType.EMBARGO,A,B);
        check(DiplomacyRules.tradeBlocked(List.of(embargo),B,A,NOW)&&!DiplomacyRules.tradeBlocked(List.of(embargo),B,C,NOW),"embargo is confined to bilateral trade");
        check(!embargo.terminate(NOW+1,999).active(NOW+1),"unilateral restrictions lift immediately");
        check(!DiplomacyRules.hostileBlocked(List.of(embargo),A,B,NOW),"embargo does not confer protection");
        var trade=active(TreatyType.TRADE,A,B);
        check(DiplomacyRules.tariffMultiplier(List.of(trade),A,B,A,NOW)==.75&&DiplomacyRules.tariffMultiplier(List.of(trade),B,A,B,NOW)==.75,"both parties grant tariff discount");
        check(DiplomacyRules.tariffMultiplier(List.of(trade),A,B,C,NOW)==1,"third-city tariffs retain full rate");
        check(DiplomacyRules.tariffMultiplier(List.of(trade,embargo),A,B,A,NOW)==1,"embargo takes priority over preference");
        deny(()->DiplomacyRules.validateNew(List.of(embargo),offer(TreatyType.TRADE,A,B),NOW));
        for(var scope:List.of(SanctionScope.TRADE,SanctionScope.DIPLOMATIC,SanctionScope.ALL)){
            var s=active(TreatyType.SANCTIONS,A,B);s=new Treaty(s.id(),s.type(),s.first(),s.second(),s.firstMayor(),s.secondMayor(),s.actor(),s.reason(),s.phase(),s.created(),s.offerUntil(),s.duration(),s.activated(),s.expires(),s.noticeUntil(),s.noticePeriod(),0,scope);
            check(DiplomacyRules.tradeBlocked(List.of(s),A,B,NOW)==(scope!=SanctionScope.DIPLOMATIC),"trade sanction scope "+scope);
            check(DiplomacyRules.negotiationsBlocked(List.of(s),A,B,NOW)==(scope!=SanctionScope.TRADE),"negotiation sanction scope "+scope);
            check(DiplomacyRules.hostileBlocked(List.of(s,accepted),A,B,NOW+900),"sanctions do not erase active pact");
        }
        var ab=active(TreatyType.VASSALAGE,A,B);var bd=active(TreatyType.VASSALAGE,B,D);var ac=active(TreatyType.VASSALAGE,A,C);
        var bloc=List.of(ab,bd,ac);
        check(DiplomacyRules.hostileBlocked(bloc,C,D,NOW),"vassal branches are protected");
        check(DiplomacyRules.defenders(bloc,D,NOW).equals(Set.of(A,B,C)),"vassal bloc receives defensive alerts");
        check(DiplomacyRules.overlord(bloc,D,NOW).equals(B),"direct suzerain remains distinct");
        deny(()->DiplomacyRules.validateNew(bloc,offer(TreatyType.VASSALAGE,D,A),NOW));
        deny(()->DiplomacyRules.validateNew(bloc,offer(TreatyType.VASSALAGE,C,B),NOW));
        deny(()->DiplomacyRules.validateSnapshot(List.of(ab,active(TreatyType.VASSALAGE,B,A)),NOW));
        deny(()->DiplomacyRules.validateSnapshot(List.of(ab,active(TreatyType.VASSALAGE,C,B)),NOW));
        var guarantee=active(TreatyType.GUARANTEE,A,B);
        check(DiplomacyRules.defenders(List.of(guarantee),B,NOW).equals(Set.of(A))&&DiplomacyRules.defenders(List.of(guarantee),A,NOW).isEmpty(),"guarantee has a directed defense obligation");
        deny(()->DiplomacyRules.validateNew(List.of(guarantee),offer(TreatyType.VASSALAGE,C,B),NOW));
        deny(()->DiplomacyRules.validateNew(bloc,offer(TreatyType.GUARANTEE,C,B),NOW));
        deny(()->DiplomacyRules.validateSnapshot(List.of(ab,guarantee),NOW));
        deny(()->DiplomacyRules.validateNew(List.of(trade),offer(TreatyType.TRADE,B,A),NOW));
        var competing=offer(TreatyType.VASSALAGE,C,B);DiplomacyRules.validateNew(List.of(offer(TreatyType.VASSALAGE,A,B)),competing,NOW);
        deny(()->DiplomacyRules.validateNew(List.of(ab,competing),competing,NOW));
        System.out.println("DiplomacyRulesSmoke OK: "+checks+" consent, expiry, graph, sanction and tariff checks");
    }
}
