package ru.neverland.mintespionage.intelligence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Captured observations, not truth probabilities or a live view into a target city. */
public final class IntelEvidence {
    private IntelEvidence(){}
    public enum Availability { KNOWN, UNKNOWN, NOT_APPLICABLE }
    public enum Comparison { OVERLAPPING, CHANGED, UNKNOWN, NO_LATER_REPORT }
    static String text(String value,int max){if(value==null||value.length()>max||value.chars().anyMatch(c->Character.isISOControl(c)||c=='§'))throw new IllegalArgumentException("Неверный текст сведений");return value;}
    public record Profile(int network,int defense,int roundingBasisPoints){
        public Profile{if(network<0||defense<0||roundingBasisPoints<0||roundingBasisPoints>9000)throw new IllegalArgumentException("Неверные условия разведки");}
        public static Profile of(int network,int defense){return new Profile(network,defense,(int)Math.max(500,Math.min(5000,2000L-200L*network+300L*defense)));}
    }
    public record Fact(String key,String label,String source,Availability availability,long sampledAt,boolean numeric,int scale,long lower,long upper,String value,String explanation){
        public Fact{key=text(key,64);label=text(label,80);source=text(source,160);value=text(value,600);explanation=text(explanation,300);Objects.requireNonNull(availability);if(!key.matches("[a-z0-9_.-]{1,64}")||source.isBlank()||sampledAt<0||scale<0||scale>4||lower<0||upper<lower||!numeric&&(lower!=0||upper!=0||scale!=0)||availability!=Availability.KNOWN&&(numeric||!value.isEmpty()))throw new IllegalArgumentException("Неверные сведения разведки");}
        public static Fact unknown(String key,String label,String source,long at,String why){return new Fact(key,label,source,Availability.UNKNOWN,at,false,0,0,0,"",why);}
        public static Fact absent(String key,String label,String source,long at,String why){return new Fact(key,label,source,Availability.NOT_APPLICABLE,at,false,0,0,0,"",why);}
        public static Fact words(String key,String label,String source,long at,String value){return new Fact(key,label,source,Availability.KNOWN,at,false,0,0,0,value,"Снимок источника на указанное время");}
        public static Fact exact(String key,String label,String source,long at,long value,int scale){return new Fact(key,label,source,Availability.KNOWN,at,true,scale,value,value,"","Точное значение источника на время наблюдения");}
        /** Rounded bins hide the exact midpoint. Width is based on the decimal magnitude, never a probability. */
        public static Fact estimate(String key,String label,String source,long at,long value,int scale,Profile profile,long minimumStep){
            if(value<0||minimumStep<1)throw new IllegalArgumentException("Неверное значение оценки");long magnitude=1;while(magnitude<=value/10)magnitude*=10;
            var stepBig=BigInteger.valueOf(magnitude).multiply(BigInteger.valueOf(profile.roundingBasisPoints())).add(BigInteger.valueOf(9999)).divide(BigInteger.valueOf(10000));
            long step=Math.max(minimumStep,Math.max(1,stepBig.longValueExact()));long lower=(value/step)*step;long upper=lower>Long.MAX_VALUE-(step-1)?Long.MAX_VALUE:lower+step-1;
            return new Fact(key,label,source,Availability.KNOWN,at,true,scale,lower,upper,"","Округлённая оценка: её точность зависит от вашей сети и защиты цели. Это не вероятность достоверности.");
        }
        public String display(){if(availability==Availability.UNKNOWN)return "неизвестно";if(availability==Availability.NOT_APPLICABLE)return "не применяется";if(!numeric)return value;String low=BigDecimal.valueOf(lower,scale).stripTrailingZeros().toPlainString(),high=BigDecimal.valueOf(upper,scale).stripTrailingZeros().toPlainString();return lower==upper?low:low+"–"+high;}
    }
    public record Receipt(UUID mission,UUID report,UUID owner,UUID target,String targetName,String missionType,long sampledAt,long expiresAt,boolean succeeded,boolean detected,boolean revealAttacker,String attackerName,Profile profile,List<Fact> facts,double successChance){
        public Receipt{Objects.requireNonNull(mission);Objects.requireNonNull(report);Objects.requireNonNull(owner);Objects.requireNonNull(target);Objects.requireNonNull(profile);targetName=text(targetName,96);attackerName=text(attackerName,96);missionType=text(missionType,64);facts=List.copyOf(facts);if(sampledAt<0||expiresAt<sampledAt||facts.size()>32||!Double.isFinite(successChance)||successChance<0||successChance>1||!succeeded&&!facts.isEmpty())throw new IllegalArgumentException("Неверная квитанция разведки");var keys=new HashSet<String>();for(var f:facts)if(!keys.add(f.key())||f.sampledAt()!=sampledAt)throw new IllegalArgumentException("Несогласованный источник отчёта");}
        public boolean expired(long now){return now>=expiresAt;}
    }
    public record State(Map<UUID,Receipt> receipts,Set<UUID> applied,long revision){
        public State{receipts=Map.copyOf(receipts);applied=Set.copyOf(applied);if(!receipts.keySet().containsAll(applied))throw new IllegalArgumentException("Результат без исходной миссии");if(receipts.size()>20_000||revision<0)throw new IllegalArgumentException("Журнал разведки заполнен");var reports=new HashSet<UUID>();for(var e:receipts.entrySet())if(!e.getKey().equals(e.getValue().mission())||!reports.add(e.getValue().report()))throw new IllegalArgumentException("Повтор ID результата разведки");}
        public static State empty(){return new State(Map.of(),Set.of(),0);}
        public State put(Receipt receipt){var old=receipts.get(receipt.mission());if(old!=null){if(!old.equals(receipt))throw new IllegalArgumentException("Результат миссии уже закреплён; повторный выбор запрещён");return this;}var next=new HashMap<>(receipts);next.put(receipt.mission(),receipt);return new State(next,applied,Math.incrementExact(revision));}
        public State applied(UUID mission){if(!receipts.containsKey(mission))throw new IllegalArgumentException("Нет результата миссии");if(applied.contains(mission))return this;var next=new HashSet<>(applied);next.add(mission);return new State(receipts,next,Math.incrementExact(revision));}
    }
    public record Check(String key,Comparison result,UUID laterReport,long laterAt,String explanation){}
    public static List<Check> compare(Receipt original,Collection<Receipt> retained){
        var later=retained.stream().filter(r->r.succeeded()&&r.owner().equals(original.owner())&&r.target().equals(original.target())&&r.sampledAt()>original.sampledAt()).sorted(Comparator.comparingLong(Receipt::sampledAt).reversed().thenComparing(Receipt::report)).toList();var result=new ArrayList<Check>();
        for(var first:original.facts()){
            Receipt found=null;Fact second=null;for(var r:later){var fact=r.facts().stream().filter(f->f.key().equals(first.key())&&f.source().equals(first.source())).findFirst();if(fact.isPresent()){found=r;second=fact.get();break;}}
            if(found==null){result.add(new Check(first.key(),Comparison.NO_LATER_REPORT,null,0,"Более позднего сохранённого отчёта с этим источником нет"));continue;}
            Comparison value;if(first.availability()!=Availability.KNOWN||second.availability()!=Availability.KNOWN||first.numeric()!=second.numeric()||first.scale()!=second.scale())value=Comparison.UNKNOWN;
            else if(first.numeric())value=first.lower()<=second.upper()&&second.lower()<=first.upper()?Comparison.OVERLAPPING:Comparison.CHANGED;
            else value=first.value().equals(second.value())?Comparison.OVERLAPPING:Comparison.CHANGED;
            result.add(new Check(first.key(),value,found.report(),found.sampledAt(),switch(value){case OVERLAPPING->"Сведения совместимы с более поздним отчётом; это не доказательство неизменности города";case CHANGED->"Более поздние сведения отличаются; город мог измениться между наблюдениями";case UNKNOWN->"Один из источников недоступен или формат изменился";default->throw new AssertionError();}));
        }return List.copyOf(result);
    }
}
