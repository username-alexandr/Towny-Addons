package ru.neverland.townytreasury.service;

import java.util.*;
import java.util.function.Predicate;
import ru.neverland.townytreasury.model.Money;

/** Observer buffer: bounded records and metadata, no Suite lock or disk IO. */
public final class BankFlows {
    public record Flow(long sequence,UUID town,long at,long amount,boolean deposit,String reason,UUID operation){}
    private final int limit;private long sequence;private boolean gap;
    private final ArrayDeque<Flow> rows=new ArrayDeque<>();
    private final Map<UUID,Long> withdrawals=new HashMap<>(),deposits=new HashMap<>();
    public BankFlows(int limit){if(limit<1||limit>4096)throw new IllegalArgumentException("flow limit");this.limit=limit;}
    public synchronized boolean add(UUID town,long at,long amount,boolean deposit,String reason,UUID operation){
        if(gap||rows.size()>=limit){gap=true;return false;}
        String text=reason.length()>1024?reason.substring(0,1024):reason;
        rows.addLast(new Flow(++sequence,town,at,amount,deposit,text,operation));
        var totals=deposit?deposits:withdrawals;totals.compute(town,(id,old)->saturating(old==null?0:old,amount));return true;
    }
    private static long saturating(long a,long b){return a>Money.MAX-b?Money.MAX:a+b;}
    public synchronized List<Flow> batch(Predicate<Flow> include,int maximum){return rows.stream().filter(include).limit(maximum).toList();}
    public synchronized boolean contains(UUID town){return withdrawals.containsKey(town)||deposits.containsKey(town);}
    public synchronized long withdrawn(UUID town){return withdrawals.getOrDefault(town,0L);}
    public synchronized long deposited(UUID town){return deposits.getOrDefault(town,0L);}
    public synchronized boolean gap(){return gap;}
    public synchronized int size(){return rows.size();}
    public synchronized void remove(List<Flow> batch){if(batch.isEmpty())return;var ids=new HashSet<Long>();for(var row:batch)ids.add(row.sequence());rows.removeIf(row->ids.contains(row.sequence()));reindex();}
    public synchronized void removeOperation(UUID id){rows.removeIf(row->id.equals(row.operation()));reindex();}
    private void reindex(){withdrawals.clear();deposits.clear();for(var row:rows){var totals=row.deposit()?deposits:withdrawals;totals.compute(row.town(),(id,old)->saturating(old==null?0:old,row.amount()));}}
}
