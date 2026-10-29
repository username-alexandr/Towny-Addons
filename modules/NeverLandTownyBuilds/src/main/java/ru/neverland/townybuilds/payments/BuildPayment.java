package ru.neverland.townybuilds.payments;

import java.util.*;
import com.google.gson.Gson;

/** Immutable purchase terms, kept in the same acknowledged image as reserved resources and applied effects. */
public record BuildPayment(Terms terms,Phase phase,int effectIndex,String auditedPhase,long phaseAt,long revision) {
    public BuildPayment(Terms terms,Phase phase,int effectIndex){this(terms,phase,effectIndex,"",terms.created(),0);}
    public BuildPayment(Terms terms,Phase phase){this(terms,phase,0);}
    public static final Gson JSON=new Gson();
    public static final int MAX=20_000;
    public enum Kind { PHYSICAL,VIRTUAL,INSURANCE }
    public enum Phase { PREPARED,PAID,ARTIFACT_PENDING,READY,APPLIED,EFFECTS_PENDING,COMPLETE,REFUNDING,REFUNDED,REJECTED }
    public record Artifact(String item,int amount) {public Artifact {if(item==null||item.isBlank()||item.length()>1_000_000||amount<1)throw new IllegalArgumentException("Повреждён артефакт оплаты");}}
    public record Terms(UUID id,UUID town,UUID actor,String actorName,Kind kind,String project,int previousLevel,int level,
                        long cents,String category,String source,String reason,long created,int bonusBlocks,
                        List<String> commands,boolean wonder,List<Artifact> artifacts) {
        public Terms {
            if(id==null||town==null||actor==null||actorName==null||actorName.isBlank()||actorName.length()>80||kind==null
                    ||project==null||!project.matches("[a-z0-9_-]{1,80}")||previousLevel<0||previousLevel>5||level<0||level>5
                    ||cents<0||cents>100_000_000_000L||category==null||category.isBlank()||category.length()>128
                    ||source==null||!Set.of("construction","insurance").contains(source)||reason==null||reason.isBlank()||reason.length()>1024
                    ||created<0||bonusBlocks<0||bonusBlocks>1_000_000||commands==null||commands.size()>64||artifacts==null||artifacts.size()>100)
                throw new IllegalArgumentException("Повреждены условия оплаты строительства");
            commands=List.copyOf(commands);artifacts=List.copyOf(artifacts);
            if(commands.stream().anyMatch(c->c==null||c.isBlank()||c.length()>2048))throw new IllegalArgumentException("Повреждена команда награды");
            if(kind==Kind.INSURANCE&&(level!=0||previousLevel!=0||bonusBlocks!=0||!commands.isEmpty()||!artifacts.isEmpty()||wonder))throw new IllegalArgumentException("Страховой перевод содержит посторонний эффект");
            if(kind!=Kind.INSURANCE&&level!=previousLevel+1)throw new IllegalArgumentException("Непоследовательный уровень постройки");
        }
    }
    public BuildPayment {if(auditedPhase==null)auditedPhase="";if(auditedPhase.length()>80)throw new IllegalArgumentException("Повреждён этап аудита");Objects.requireNonNull(terms);Objects.requireNonNull(phase);if(effectIndex<0||effectIndex>terms.commands().size()+1)throw new IllegalArgumentException("Повреждён указатель эффектов");if(phaseAt==0)phaseAt=terms.created();if(phaseAt<terms.created()||revision<0)throw new IllegalArgumentException("Повреждена последовательность оплаты");}
    public String auditKey(){return phase.name()+":"+effectIndex+":"+revision;}
    public String auditOutcome(){return switch(phase){case PREPARED->"PREPARED";case PAID,READY,APPLIED->"PENDING";case ARTIFACT_PENDING,EFFECTS_PENDING->"REVIEW";case REFUNDING->"PAYING";case COMPLETE->"COMPLETED";case REFUNDED->"REFUNDED";case REJECTED->"REJECTED";};}
    public boolean needsAudit(){return !auditKey().equals(auditedPhase);}
    public BuildPayment auditConfirmed(){return new BuildPayment(terms,phase,effectIndex,auditKey(),phaseAt,revision);}
    public boolean pending(){return !Set.of(Phase.COMPLETE,Phase.REFUNDED,Phase.REJECTED).contains(phase);}
    private BuildPayment advance(Phase next,int index){return new BuildPayment(terms,next,index,auditedPhase,Math.max(System.currentTimeMillis(),Math.addExact(phaseAt,1)),Math.addExact(revision,1));}
    public BuildPayment effectConfirmed(){if(phase!=Phase.EFFECTS_PENDING)throw new IllegalStateException("Эффект не ожидал подтверждения");return advance(Phase.APPLIED,effectIndex+1);}
    public BuildPayment skipEmptyBonus(){if(phase!=Phase.APPLIED||effectIndex!=0||terms.bonusBlocks()!=0)throw new IllegalStateException("Нет пустого бонуса для пропуска");return advance(Phase.APPLIED,1);}
    public BuildPayment phase(Phase next){
        boolean valid=next==phase||switch(phase){
            case PREPARED->Set.of(Phase.PAID,Phase.REJECTED).contains(next);
            case PAID->Set.of(Phase.ARTIFACT_PENDING,Phase.READY,Phase.REFUNDING).contains(next);
            case ARTIFACT_PENDING->Set.of(Phase.READY,Phase.REFUNDING).contains(next);
            case READY->Set.of(Phase.APPLIED,Phase.REFUNDING).contains(next);
            case APPLIED->Set.of(Phase.EFFECTS_PENDING,Phase.COMPLETE).contains(next);
            case EFFECTS_PENDING->Set.of(Phase.APPLIED,Phase.COMPLETE).contains(next);
            case REFUNDING->next==Phase.REFUNDED;
            default->false;
        };
        if(!valid)throw new IllegalStateException("Недопустимый переход оплаты "+phase+" → "+next);
        return next==phase?this:advance(next,effectIndex);
    }
}
