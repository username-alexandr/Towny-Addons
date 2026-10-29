package ru.neverland.townytreasury.debits;

import java.util.*;

/** Frozen limit evidence. Historical receipts are not retroactively reclassified or charged again. */
public record SpendingAdmission(String mode,long revision,long perOperation,long perDay,long spent,long reserved,long checkedAt) {
    public static final UUID SYSTEM=new UUID(0,0);
    public static final long DAY=86_400_000L,MAX=100_000_000_000L;
    private static final Set<String> MODES=Set.of("LEGACY","NO_COUNCIL","POLICY","SYSTEM","REFUND","UNAVAILABLE");
    public SpendingAdmission{if(!MODES.contains(mode)||revision<0||perOperation<0||perOperation>MAX||perDay<0||perDay>MAX||spent<0||reserved<0||checkedAt<0)throw new IllegalArgumentException("Повреждены условия ограничения расходов");}
    public static SpendingAdmission legacy(){return new SpendingAdmission("LEGACY",0,0,0,0,0,0);}
    public record Usage(long spent,long reserved){public Usage{if(spent<0||reserved<0)throw new IllegalArgumentException();}public long total(){return Math.addExact(spent,reserved);}}
    public static Usage usage(Collection<AsyncDebit> rows,UUID town,UUID actor,long now){
        long spent=0,reserved=0,cutoff=Math.max(0,now-DAY);for(var d:rows){var t=d.terms();if(t.kind()!=AsyncDebit.Kind.DEBIT||t.actor().equals(SYSTEM)||d.admission().mode().equals("LEGACY")||!t.town().equals(town)||!t.actor().equals(actor))continue;
            if(d.unresolved())reserved=Math.addExact(reserved,t.cents());else if(d.state()==AsyncDebit.State.PAID&&d.changedAt()>cutoff)spent=Math.addExact(spent,t.cents());
        }return new Usage(spent,reserved);
    }
    public String refusal(long cents){
        if(cents<=0||cents>MAX)throw new IllegalArgumentException("Неверная сумма нового расхода");
        if(mode.equals("UNAVAILABLE"))return "Ограничения совета сейчас недоступны; банк не вызывался. Повторите после восстановления совета";
        if(!mode.equals("POLICY"))return "";
        if(perOperation>0&&cents>perOperation)return "Сумма превышает установленный мэром предел одной покупки";
        if(perDay>0&&(spent>perDay||reserved>perDay-spent||cents>perDay-spent-reserved))return "Лимит расходов за последние 24 часа исчерпан с учётом ожидающих платежей";
        return "";
    }
    public void validate(AsyncDebit.Terms terms,AsyncDebit.State state,long createdAt){
        if(mode.equals("LEGACY")){if(!equals(legacy()))throw new IllegalArgumentException("Повреждены исторические условия лимита");return;}
        if(mode.equals("UNAVAILABLE")||checkedAt!=createdAt)throw new IllegalArgumentException("Неподтверждённые условия ограничения расходов");
        if(mode.equals("REFUND")!=(terms.kind()==AsyncDebit.Kind.REFUND)||!mode.equals("REFUND")&&mode.equals("SYSTEM")!=terms.actor().equals(SYSTEM))throw new IllegalArgumentException("Условия ограничения не соответствуют владельцу платежа");
        if(!mode.equals("POLICY")&&(revision!=0||perOperation!=0||perDay!=0||spent!=0||reserved!=0))throw new IllegalArgumentException("Неверные условия освобождения от лимита");
        if(state!=AsyncDebit.State.REJECTED&&!refusal(terms.cents()).isEmpty())throw new IllegalArgumentException("Принятый платёж превышает сохранённый лимит");
    }
    public Map<String,Object> view(){return Map.of("mode",mode,"revision",revision,"perOperation",perOperation,"perDay",perDay,"spent",spent,"reserved",reserved,"checkedAt",checkedAt);}
    public static SpendingAdmission read(Map<String,Object> row){return new SpendingAdmission(String.valueOf(row.get("mode")),number(row,"revision"),number(row,"perOperation"),number(row,"perDay"),number(row,"spent"),number(row,"reserved"),number(row,"checkedAt"));}
    private static long number(Map<String,Object> row,String key){try{return new java.math.BigDecimal(String.valueOf(row.get(key))).longValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("Неверное поле лимита: "+key,e);}}
}
