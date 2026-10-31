package cc.crafto.ui.server;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DevelopmentEffectsTest {
    Map<String,Object> upkeep(){return new HashMap<>(Map.of("known",true,"reason","Полный период","periodSeconds",7200,"before",Map.of("cents",100L,"resources",Map.of("wood",1500L)),"after",Map.of("cents",300L,"resources",Map.of("wood",2500L)),"delta",Map.of("cents",200L,"resources",Map.of("wood",1000L)),"buildings",List.of(),"existingInvoices",List.of(),"assumptions",List.of("Новая площадка должна принадлежать городу")));}
    Map<String,Object> power(){return new HashMap<>(Map.of("known",true,"reason","После завершения","periodSeconds",10,"before",Map.of("generation",10L,"demand",15L,"deficit",15L),"after",Map.of("generation",20L,"demand",15L,"deficit",0L),"delta",Map.of("generation",10L,"demand",0L,"deficit",-15L),"buildings",List.of(),"assumptions",List.of("Заряд накопителей не включён")));}
    @Test void exactUnitsAndPeriodAreReadable(){var s=DevelopmentEffects.decode(upkeep(),true);assertTrue(s.known());assertEquals(7200,s.period());assertTrue(s.rows().getFirst().title().contains("2 ч."));assertTrue(s.rows().get(1).detail().contains("1.5 → 2.5"));assertTrue(s.rows().getFirst().detail().contains("1 монет"));}
    @Test void fullConsumerDeficitIsNotSimpleDemandMinusGeneration(){var s=DevelopmentEffects.decode(power(),false);assertTrue(s.rows().get(2).detail().contains("15"));assertTrue(s.rows().get(2).detail().contains("0"));}
    @Test void unknownHasNoInventedZeroAmounts(){var s=DevelopmentEffects.decode(Map.of("known",false,"reason","Модуль приостановлен"),true);assertFalse(s.known());assertTrue(s.rows().isEmpty());assertEquals("Модуль приостановлен",s.reason());}
    @Test void rejectsInconsistentDifference(){var v=upkeep();v.put("delta",Map.of("cents",0L,"resources",Map.of("wood",1000L)));assertThrows(IllegalArgumentException.class,()->DevelopmentEffects.decode(v,true));}
    @Test void rejectsResourceDifference(){var v=upkeep();v.put("delta",Map.of("cents",200L,"resources",Map.of("wood",1L)));assertThrows(IllegalArgumentException.class,()->DevelopmentEffects.decode(v,true));}
    @Test void rejectsNegativeFutureCost(){var v=upkeep();v.put("after",Map.of("cents",-1L,"resources",Map.of()));assertThrows(IllegalArgumentException.class,()->DevelopmentEffects.decode(v,true));}
    @Test void rejectsFractionalProtocolValues(){var v=power();v.put("periodSeconds",10.5);assertThrows(IllegalArgumentException.class,()->DevelopmentEffects.decode(v,false));}
    @Test void oldInvoicesAppearSeparately(){var v=upkeep();v.put("existingInvoices",List.of(Map.of("id",UUID.randomUUID().toString(),"project","generator","cents",900L,"resources",Map.of("wood",3000L),"phase","READY")));var s=DevelopmentEffects.decode(v,true);assertTrue(s.rows().stream().anyMatch(r->r.title().startsWith("Ранее выставленный")&&r.detail().contains("9 монет")));assertTrue(s.rows().getFirst().detail().contains("после плана: 3 монет"));}
    @Test void preservesEveryAssumptionAndPublishesNoAction(){var v=new CivicView();new DevelopmentEffects(DevelopmentEffects.decode(upkeep(),true),DevelopmentEffects.decode(power(),false)).populate(v);assertTrue(v.rows.stream().anyMatch(r->r.detail().contains("Заряд накопителей")));assertTrue(v.rows.stream().allMatch(r->r.action()==null));}
}
