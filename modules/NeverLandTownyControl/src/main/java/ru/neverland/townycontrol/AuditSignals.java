package ru.neverland.townycontrol;

import java.math.BigDecimal;
import java.util.*;
import ru.neverland.core.AuditRecord;

/** Review hints only. Never a balance, punishment, automatic retry, or proof of abuse. */
final class AuditSignals {
    enum Type { REVIEW, CHAIN }
    record Card(Type type,String title,String reason,List<AuditRecord> evidence){Card{evidence=List.copyOf(evidence);}}
    private static final Set<String> PENDING=Set.of("PREPARED","PENDING","PAYING","UNCONFIRMED","REVIEW","REVIEW_CORE","REVIEW_DEBIT","REVIEW_CREDIT","REVIEW_BANK","REVIEW_WORLD");
    static final int MAX_ROWS=20_000,MAX_CARDS=200;
    static final long MAX_CHARACTERS=8_388_608;
    static long characters(AuditRecord r){return r.fields().stream().mapToLong(String::length).sum();}
    private static BigDecimal money(AuditRecord r){
        if(r.money().length()>32)throw new IllegalArgumentException("Слишком длинная сумма в квитанции "+r.id());
        var amount=new BigDecimal(r.money());if(amount.precision()>20||Math.abs((long)amount.scale())>6)throw new IllegalArgumentException("Необычный формат суммы: сверьте квитанцию "+r.id());return amount;
    }
    static List<Card> inspect(List<AuditRecord> records,long now){
        if(records.size()>MAX_ROWS)throw new IllegalArgumentException("Больше 20000 записей за период; сузьте период проверки");
        long characters=0;for(var r:records){characters+=characters(r);if(characters>MAX_CHARACTERS)throw new IllegalArgumentException("Слишком большой объём записей; сузьте период проверки");}
        var unique=new LinkedHashMap<String,AuditRecord>();
        for(var r:records){String key=r.module()+":"+r.id();var old=unique.putIfAbsent(key,r);if(old!=null&&!old.equals(r))throw new IllegalArgumentException("Противоречивый ID квитанции "+r.id());}
        var sorted=unique.values().stream().sorted(Comparator.comparingLong(AuditRecord::at).thenComparing(AuditRecord::id)).toList();
        var latest=new HashMap<String,List<AuditRecord>>();for(var r:sorted)if(!r.operation().isBlank()){
            String key=r.module()+":"+r.operation();var group=latest.get(key);
            if(group==null||group.get(0).at()<r.at()){group=new ArrayList<>();latest.put(key,group);}
            if(group.size()>=16)throw new IllegalArgumentException("Больше 16 одновременных записей операции; нужна точная сверка по ID");group.add(r);
        }
        var cards=new ArrayList<Card>();
        for(var group:latest.values())if(group.stream().anyMatch(r->PENDING.contains(r.outcome()))&&group.get(0).at()<=now-600_000L){
            String statuses=String.join(", ",group.stream().map(AuditRecord::outcome).distinct().sorted().toList());
            cards.add(new Card(Type.REVIEW,"Проверить завершение операции","Последние найденные записи старше 10 минут: "+statuses+". Сверьте с текущим состоянием аддона; журнал может запаздывать.",group));
        }
        // Only paired, completed bank transfers. BANK_LEG, command observations and sale prices
        // are deliberately excluded so one payment cannot become several supposed transfers.
        var amounts=new HashMap<String,BigDecimal>();var transfers=new ArrayList<AuditRecord>();
        for(var r:sorted)if(r.kind().equals("BANK_TRANSFER")&&r.outcome().equals("COMPLETED")&&r.intercity()&&!r.money().isBlank()){
            var amount=money(r);if(amount.compareTo(new BigDecimal("1000"))>=0){amounts.put(r.module()+":"+r.id(),amount);transfers.add(r);}
        }
        var incoming=new HashMap<String,ArrayDeque<AuditRecord>>();long comparisons=0;
        for(var next:transfers){
            var prior=incoming.get(next.from().town());
            if(prior!=null){
                while(!prior.isEmpty()&&prior.peekFirst().at()<next.at()-900_000L)prior.removeFirst();
                for(var first:prior){
                    if(++comparisons>1_000_000)throw new IllegalArgumentException("Слишком много связанных переводов; сузьте период проверки");
                    if(first.from().town().equals(next.to().town())||first.id().equals(next.id()))continue;
                    BigDecimal a=amounts.get(first.module()+":"+first.id()),b=amounts.get(next.module()+":"+next.id());
                    if(a.subtract(b).abs().compareTo(a.multiply(new BigDecimal("0.10")))<=0){
                        cards.add(new Card(Type.CHAIN,"Быстрая передача через третий город","Два перевода от 1000 монет за 15 минут, суммы различаются не более чем на 10%. Это повод для сверки, а не доказательство нарушения.",List.of(first,next)));
                        if(cards.size()>MAX_CARDS)throw new IllegalArgumentException("Больше 200 карточек; сузьте период проверки");
                    }
                }
            }
            incoming.computeIfAbsent(next.to().town(),x->new ArrayDeque<>()).addLast(next);
        }
        if(cards.size()>MAX_CARDS)throw new IllegalArgumentException("Больше 200 карточек; сузьте период проверки");
        cards.sort(Comparator.comparingLong((Card c)->c.evidence().get(c.evidence().size()-1).at()).reversed().thenComparing(c->c.evidence().get(0).id()));return List.copyOf(cards);
    }
}
