package ru.neverland.townyresearch.licenses;

import java.util.*;
import ru.neverland.townyresearch.model.Technology;

/** Frozen trade terms. A license grants a temporary effect, never a learned research level. */
public record TechLicense(Terms terms, UUID buyerActor, Phase phase, int remaining,
                          int offerRemaining, boolean cancelRequested, long revision,
                          long createdAt, String note, boolean audited, Event event, LicenseActor actor, long changedAt) {
    public TechLicense(Terms terms,UUID buyerActor,Phase phase,int remaining,int offerRemaining,boolean cancelRequested,long revision,long createdAt,String note,boolean audited,Event event){this(terms,buyerActor,phase,remaining,offerRemaining,cancelRequested,revision,createdAt,note,audited,event,LicenseActor.system(),System.currentTimeMillis());}
    public enum Event { STATE, PAYMENT, REFUND }
    public TechLicense(Terms terms,UUID buyerActor,Phase phase,int remaining,int offerRemaining,boolean cancelRequested,long revision,long createdAt,String note,boolean audited){this(terms,buyerActor,phase,remaining,offerRemaining,cancelRequested,revision,createdAt,note,audited,Event.STATE);}
    public static final int MAX_RECORDS=10_000;
    public enum Phase { OFFERED, ACCEPTING, FUNDING, FUNDED, PAYING, PAY_REVIEW, ACTIVE, PAUSED, REFUNDING, CANCELLED, EXPIRED, REJECTED }
    public record Terms(UUID id,UUID seller,UUID buyer,UUID sellerActor,String technology,int level,
                        double bonus,long cents,int duration) {
        public Terms {
            Objects.requireNonNull(id);Objects.requireNonNull(seller);Objects.requireNonNull(buyer);Objects.requireNonNull(sellerActor);
            if(seller.equals(buyer)||!Technology.IDS.contains(technology)||level<1||level>5||!Double.isFinite(bonus)||bonus<=0||bonus>0.5||cents<1||cents>100_000_000_000L||duration<60||duration>604800)throw new IllegalArgumentException("Неверные условия технологической лицензии");
        }
        public UUID operation(String step){return UUID.nameUUIDFromBytes(("research-license:"+id+":"+step).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        public String debitReason(){return "Лицензия "+id+" / "+technology+" / "+seller;}
        public String refundReason(){return "Полный возврат лицензии "+id;}
    }
    public TechLicense {
        Objects.requireNonNull(terms);Objects.requireNonNull(phase);Objects.requireNonNull(event);Objects.requireNonNull(actor);if(changedAt<0)throw new IllegalArgumentException("Неверное время решения");if(event==Event.PAYMENT&&phase!=Phase.ACTIVE||event==Event.REFUND&&phase!=Phase.CANCELLED)throw new IllegalArgumentException("Аудит оплаты не соответствует состоянию лицензии");
        if(remaining<0||remaining>terms.duration()||offerRemaining<0||offerRemaining>604800||revision<0||createdAt<0||note==null||note.length()>512)throw new IllegalArgumentException("Повреждена квитанция лицензии");
        if(!Set.of(Phase.OFFERED,Phase.CANCELLED,Phase.EXPIRED).contains(phase)&&buyerActor==null)throw new IllegalArgumentException("Нет подтверждения покупателя");
    }
    public static TechLicense offered(Terms t,int offerSeconds,long now){return new TechLicense(t,null,Phase.OFFERED,t.duration(),offerSeconds,false,0,now,"",false);}
    public TechLicense accepted(UUID actor){if(phase!=Phase.OFFERED)throw new IllegalStateException("Предложение уже обработано");return new TechLicense(terms,Objects.requireNonNull(actor),Phase.ACCEPTING,remaining,offerRemaining,false,revision+1,createdAt,"Покупатель подтвердил точные условия",false);}
    public TechLicense phase(Phase next,String reason){return new TechLicense(terms,buyerActor,next,remaining,offerRemaining,cancelRequested,revision+1,createdAt,reason,false);}
    public TechLicense cancel(String reason){return new TechLicense(terms,buyerActor,phase,remaining,offerRemaining,true,revision+1,createdAt,reason,false);}
    public TechLicense paid(String reason){return new TechLicense(terms,buyerActor,Phase.ACTIVE,remaining,offerRemaining,cancelRequested,revision+1,createdAt,reason,false,Event.PAYMENT);}
    public TechLicense refunded(String reason){return new TechLicense(terms,buyerActor,Phase.CANCELLED,remaining,offerRemaining,cancelRequested,revision+1,createdAt,reason,false,Event.REFUND);}
    public TechLicense auditAck(){return new TechLicense(terms,buyerActor,phase,remaining,offerRemaining,cancelRequested,revision,createdAt,note,true,event,actor,changedAt);}
    public TechLicense by(LicenseActor who){return new TechLicense(terms,buyerActor,phase,remaining,offerRemaining,cancelRequested,revision,createdAt,note,audited,event,who,System.currentTimeMillis());}
    /** Reserve time durably before enabling its effect; maximum crash loss is one small window. */
    public TechLicense reserveWindow(int seconds){if(phase!=Phase.ACTIVE||!audited||seconds<1||seconds>5||seconds>remaining)throw new IllegalArgumentException("Неверное окно лицензии");return new TechLicense(terms,buyerActor,phase,remaining-seconds,offerRemaining,cancelRequested,revision,createdAt,note,audited,event,actor,changedAt);}
    public TechLicense returnWindow(int seconds){if(!Set.of(Phase.ACTIVE,Phase.PAUSED).contains(phase)||seconds<0||seconds>5||remaining+seconds>terms.duration())throw new IllegalArgumentException("Неверный остаток окна лицензии");return new TechLicense(terms,buyerActor,phase,remaining+seconds,offerRemaining,cancelRequested,revision,createdAt,note,audited,event,actor,changedAt);}
    public boolean terminal(){return Set.of(Phase.CANCELLED,Phase.EXPIRED,Phase.REJECTED).contains(phase);}
    public boolean reservesSlot(){return phase!=Phase.OFFERED&&!terminal();}
    public TechLicense advance(int seconds,boolean usable){
        if(seconds<0||seconds>60)throw new IllegalArgumentException("Шаг времени 0..60 секунд");
        if(phase==Phase.OFFERED){int left=Math.max(0,offerRemaining-seconds);return left==offerRemaining?this:new TechLicense(terms,buyerActor,left==0?Phase.EXPIRED:phase,remaining,left,cancelRequested,revision+(left==0?1:0),createdAt,left==0?"Срок предложения истёк":note,left!=0&&audited,left==0?Event.STATE:event,left==0?LicenseActor.system():actor,left==0?System.currentTimeMillis():changedAt);}
        if(phase!=Phase.ACTIVE||!usable)return this;int left=Math.max(0,remaining-seconds);return left==remaining?this:new TechLicense(terms,buyerActor,left==0?Phase.EXPIRED:phase,left,offerRemaining,cancelRequested,revision+(left==0?1:0),createdAt,left==0?"Оплаченный срок завершён":note,left!=0&&audited,left==0?Event.STATE:event,left==0?LicenseActor.system():actor,left==0?System.currentTimeMillis():changedAt);
    }
    public TechLicense offerConsumed(int seconds){if(seconds<0)throw new IllegalArgumentException("Отрицательное время");if(phase!=Phase.OFFERED)return this;int left=Math.max(0,offerRemaining-seconds);return left==offerRemaining?this:new TechLicense(terms,buyerActor,left==0?Phase.EXPIRED:phase,remaining,left,cancelRequested,revision+(left==0?1:0),createdAt,left==0?"Срок предложения истёк":note,left!=0&&audited,left==0?Event.STATE:event,left==0?LicenseActor.system():actor,left==0?System.currentTimeMillis():changedAt);}
    /** Already consumed active ticks can be persisted after an administrator pauses the row. */
    public TechLicense consumed(int seconds){if(seconds<0)throw new IllegalArgumentException("Отрицательное время");if(phase!=Phase.ACTIVE&&phase!=Phase.PAUSED)return this;int left=Math.max(0,remaining-seconds);return left==remaining?this:new TechLicense(terms,buyerActor,left==0?Phase.EXPIRED:phase,left,offerRemaining,cancelRequested,revision+(left==0?1:0),createdAt,left==0?"Оплаченный срок завершён":note,left!=0&&audited,left==0?Event.STATE:event,left==0?LicenseActor.system():actor,left==0?System.currentTimeMillis():changedAt);}
    public Map<String,Object> view(){var t=terms;var out=new LinkedHashMap<String,Object>();out.put("id",t.id().toString());out.put("seller",t.seller().toString());out.put("buyer",t.buyer().toString());out.put("sellerActor",t.sellerActor().toString());out.put("buyerActor",buyerActor==null?"":buyerActor.toString());out.put("technology",t.technology());out.put("level",t.level());out.put("bonus",t.bonus());out.put("cents",t.cents());out.put("duration",t.duration());out.put("remaining",remaining);out.put("offerRemaining",offerRemaining);out.put("phase",phase.name());out.put("revision",revision);out.put("createdAt",createdAt);out.put("note",note);out.put("cancelRequested",cancelRequested);return Map.copyOf(out);}
}
