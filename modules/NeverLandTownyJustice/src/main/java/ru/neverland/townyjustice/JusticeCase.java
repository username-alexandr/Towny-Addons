package ru.neverland.townyjustice;
import java.util.*;
/** Money, charge, participants and sentence are immutable after issue. */
public record JusticeCase(UUID id,UUID town,UUID subject,UUID issuer,Kind kind,long amount,int hours,String reason,long created,long due,Phase phase,UUID payment,UUID payout,UUID hunter,UUID prison,long captureStarted,long capturedAt){
    public enum Kind{FINE,WARRANT}
    public enum Phase{OPEN,PAYING,PAID,FUNDING,WANTED,ARRESTING,JAILED,RELEASING,RELEASED,REFUNDING,CANCELLED}
    public JusticeCase{Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(subject);Objects.requireNonNull(issuer);Objects.requireNonNull(kind);Objects.requireNonNull(phase);reason=reason(reason);
        if(amount<0||amount>100000000000L||created<0||due<=created||captureStarted<0||capturedAt<0||capturedAt>0&&capturedAt<captureStarted||kind==Kind.WARRANT&&Set.of(Phase.FUNDING,Phase.WANTED).contains(phase)&&(payout!=null||hunter!=null||prison!=null||captureStarted!=0||capturedAt!=0)||kind==Kind.FINE&&(amount==0||hours!=0||!Set.of(Phase.OPEN,Phase.PAYING,Phase.PAID,Phase.CANCELLED).contains(phase)||payout!=null||hunter!=null||prison!=null||captureStarted!=0||capturedAt!=0)||kind==Kind.WARRANT&&(hours<1||hours>168||Set.of(Phase.OPEN,Phase.PAYING,Phase.PAID).contains(phase))||kind==Kind.WARRANT&&Set.of(Phase.ARRESTING,Phase.JAILED,Phase.RELEASING,Phase.RELEASED).contains(phase)&&(hunter==null||prison==null||captureStarted==0)||hunter!=null&&hunter.equals(subject)||Set.of(Phase.JAILED,Phase.RELEASED).contains(phase)&&capturedAt==0)throw new IllegalArgumentException("Повреждено судебное дело");}
    public static String reason(String text){if(text==null||text.strip().length()<5||text.length()>160||text.chars().anyMatch(c->Character.isISOControl(c)||c=='§'||c=='&'))throw new IllegalArgumentException("Причина: 5–160 символов, без переносов и цветовых кодов");return text.strip();}
    public JusticeCase phase(Phase p){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,p,payment,payout,hunter,prison,captureStarted,capturedAt);}
    public JusticeCase payment(UUID tx,Phase p){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,p,tx,payout,hunter,prison,captureStarted,capturedAt);}
    public JusticeCase settlement(UUID tx,Phase p){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,p,payment,tx,hunter,prison,captureStarted,capturedAt);}
    public JusticeCase arrest(UUID by,UUID jail,long now){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,Phase.ARRESTING,payment,null,by,jail,now,0);}
    public JusticeCase captured(UUID tx,long now){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,Phase.JAILED,payment,tx,hunter,prison,captureStarted,now);}
    public JusticeCase retryCapture(){return new JusticeCase(id,town,subject,issuer,kind,amount,hours,reason,created,due,Phase.WANTED,payment,null,null,null,0,0);}
    public boolean terminal(){return Set.of(Phase.PAID,Phase.RELEASED,Phase.CANCELLED).contains(phase);}
}
