package ru.neverland.townycrime;
import java.util.*;
/** The incident ID is also the durable Resources reservation ID. */
public record CrimeState(UUID town,double level,long nextCycle,long nextIncident,Incident incident){
    public CrimeState{Objects.requireNonNull(town);if(!CrimeSettings.finite(level,0,100)||nextCycle<0||nextIncident<0)throw new IllegalArgumentException("Повреждено состояние преступности");}
    public record Incident(UUID id,String kind,String resource,long amount,String phase,long created,long until){
        public Incident{Objects.requireNonNull(id);if(!Set.of("BURGLARY","EXTORTION").contains(kind)||!Set.of("PLANNED","APPLIED","SKIPPED","CLOSED","CANCELLED").contains(phase)||created<0||until<created||amount<0||amount>1000000000L||resource==null||kind.equals("BURGLARY")&&(!CrimeSettings.RESOURCE_IDS.contains(resource)||amount<1)||kind.equals("EXTORTION")&&(!resource.isEmpty()||amount!=0))throw new IllegalArgumentException("Повреждено происшествие");}
        public Incident phase(String p){return new Incident(id,kind,resource,amount,p,created,until);}
        public boolean pending(){return Set.of("PLANNED","APPLIED","SKIPPED").contains(phase);}
        public boolean active(long now){return !Set.of("SKIPPED","CANCELLED","PLANNED").contains(phase)&&now<until;}
    }
    public CrimeState incident(Incident i){return new CrimeState(town,level,nextCycle,nextIncident,i);}
}
