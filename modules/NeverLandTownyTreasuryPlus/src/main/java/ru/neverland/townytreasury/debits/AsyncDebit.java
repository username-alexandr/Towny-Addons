package ru.neverland.townytreasury.debits;

import java.util.*;
import ru.neverland.townytreasury.model.*;

/** Immutable caller-owned payment identity. Receipt retention is bounded; identities are never recycled. */
public record AsyncDebit(Terms terms,State state,long createdAt,long changedAt,String review,boolean audited) {
    public static final int MAX_RECEIPTS=50_000;
    public enum Kind { DEBIT, REFUND }
    public enum State { PREPARED,INVOKING,PAID,REJECTED,REVIEW }
    public record Terms(UUID operation,UUID town,String category,String source,long cents,String reason,UUID actor,String owner,Kind kind,UUID backingDebit) {
        public Terms(UUID operation,UUID town,String category,String source,long cents,String reason,UUID actor,String owner){this(operation,town,category,source,cents,reason,actor,owner,Kind.DEBIT,null);}
        public Terms {Objects.requireNonNull(operation);Objects.requireNonNull(town);Objects.requireNonNull(actor);Objects.requireNonNull(kind);Budget.parse(category);if(kind==Kind.DEBIT&&backingDebit!=null||kind==Kind.REFUND&&(backingDebit==null||backingDebit.equals(operation)))throw new IllegalArgumentException("Неверная обеспечивающая квитанция возврата");
            if(cents<=0||cents>100_000_000_000L||source==null||!source.matches("[a-z0-9_/-]{1,64}")||reason==null||reason.isBlank()||reason.length()>384||owner==null||!owner.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Некорректные условия асинхронного списания");
            if(reason.chars().anyMatch(ch->ch<' '&&ch!='\t'))throw new IllegalArgumentException("Причина платежа должна занимать одну строку");
        }
        public CityLedger.Pending pending(long now){return new CityLedger.Pending(operation,now,cents,Budget.parse(category),source,reason);}
    }
    public AsyncDebit(Terms terms,State state,long createdAt,long changedAt,String review){this(terms,state,createdAt,changedAt,review,false);}
    public AsyncDebit {Objects.requireNonNull(terms);Objects.requireNonNull(state);if(createdAt<0||changedAt<createdAt||review==null||review.length()>512)throw new IllegalArgumentException("Повреждена квитанция асинхронного списания");}
    public AsyncDebit state(State next,long now,String why){return new AsyncDebit(terms,next,createdAt,Math.max(createdAt,now),why,false);}
    public AsyncDebit auditAck(){return new AsyncDebit(terms,state,createdAt,changedAt,review,true);}
    public boolean unresolved(){return state==State.PREPARED||state==State.INVOKING||state==State.REVIEW;}
    public Map<String,Object> view(){var t=terms;return Map.ofEntries(Map.entry("operation",t.operation().toString()),Map.entry("town",t.town().toString()),Map.entry("category",t.category()),Map.entry("source",t.source()),Map.entry("cents",t.cents()),Map.entry("reason",t.reason()),Map.entry("actor",t.actor().toString()),Map.entry("owner",t.owner()),Map.entry("status",state.name()),Map.entry("createdAt",createdAt),Map.entry("changedAt",changedAt),Map.entry("review",review),Map.entry("kind",t.kind().name()),Map.entry("backingDebit",t.backingDebit()==null?"":t.backingDebit().toString()),Map.entry("audited",audited));}
    public void requireSame(Terms proposed){if(!terms.equals(proposed))throw new IllegalArgumentException("Этот ID уже закреплён за другими условиями платежа");}
}
