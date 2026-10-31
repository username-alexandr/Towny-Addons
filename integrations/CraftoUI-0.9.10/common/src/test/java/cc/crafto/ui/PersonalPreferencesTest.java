package cc.crafto.ui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PersonalPreferencesTest {
    private ContractHudState.Entry entry(int n){return new ContractHudState.Entry(new UUID(0,n).toString(),"Заказ "+n,0,10,false,ContractHudState.Status.ACTIVE);}
    @Test void automaticAndManualOrdering(){var p=new PersonalPreferences();var a=entry(1);var b=entry(2);var c=entry(3);var list=List.of(a,b);assertEquals(list,p.selected(list));p.move(b.id(),-1,list);assertFalse(p.automatic);assertEquals(List.of(b,a),p.selected(List.of(a,b,c)));}
    @Test void unpinAllStaysEmpty(){var p=new PersonalPreferences();var a=entry(1);p.pin(a.id(),List.of(a));assertFalse(p.automatic);assertTrue(p.selected(List.of(a,entry(2))).isEmpty());}
    @Test void missingContractsNeverRender(){var p=new PersonalPreferences();p.pinned.add(entry(3).id());p.pinned.add(entry(1).id());assertEquals(List.of(entry(1),entry(2)),p.selected(List.of(entry(1),entry(2))));}
    @Test void notificationsDeduplicateAndStayBounded(){var p=new PersonalPreferences();assertTrue(p.announce("a"));assertFalse(p.announce("a"));for(int i=0;i<150;i++)p.announce("b"+i);assertEquals(128,p.announced.size());assertFalse(p.announce("b149"));}
    @Test void corruptSettingsAreClamped(){var p=new PersonalPreferences();p.x=Float.NaN;p.y=-20;p.scale=Float.POSITIVE_INFINITY;p.rows=999;p.pinned=null;p.favorites=Arrays.asList("a",null,"a"," ");p.sanitize();assertEquals(1,p.x);assertEquals(0,p.y);assertEquals(1,p.scale);assertEquals(5,p.rows);assertEquals(List.of("a"),p.favorites);assertNotNull(p.pinned);}
    @Test void perServerProfilesDoNotShareFavorites(){var a=new PersonalPreferences();var b=new PersonalPreferences();a.favorite("link:warehouse");assertTrue(b.favorites.isEmpty());a.favorite("link:warehouse");assertTrue(a.favorites.isEmpty());}
    @Test void russianSearchNormalizesCaseAndYo(){assertTrue(QuickSearch.matches("Казённый склад","ресурсы warehouse","КАЗЕННЫЙ склад"));assertTrue(QuickSearch.matches("Личные заказы","контракты orders","Контр"));assertFalse(QuickSearch.matches("Склад","ресурсы","склад награды"));}
}
