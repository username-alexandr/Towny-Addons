package ru.neverland.townyresources.model;
import java.util.*;
public record Reservation(UUID town, Map<Resource,Long> amounts, Status status,String purpose,String actor,long created,String changedBy) {
    public enum Status { REQUESTED, HELD, CONSUMED, RELEASED }
    public Reservation(UUID town,Map<Resource,Long> amounts,Status status){this(town,amounts,status,"","",0,"");}
    public Reservation(UUID town,Map<Resource,Long> amounts,Status status,String purpose,String actor,long created){this(town,amounts,status,purpose,actor,created,actor);}
    public Reservation {
        Objects.requireNonNull(town);Objects.requireNonNull(status);amounts=Amounts.copy(amounts);
        if(purpose==null||purpose.length()>128||purpose.chars().anyMatch(Character::isISOControl)||actor==null||created<0)throw new IllegalArgumentException("Повреждено назначение резерва");
        if(!actor.isEmpty())UUID.fromString(actor);
        if(changedBy==null)throw new IllegalArgumentException("Не указан участник операции");if(!changedBy.isEmpty())UUID.fromString(changedBy);
        if(status==Status.REQUESTED&&purpose.isBlank())throw new IllegalArgumentException("Заявке нужно назначение");
    }
    public Reservation state(Status next){return new Reservation(town,amounts,next,purpose,actor,created,changedBy);}
    public Reservation state(Status next,UUID operator){return new Reservation(town,amounts,next,purpose,actor,created,operator==null?"":operator.toString());}
    public Reservation finish(boolean consume) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return state(consume?Status.CONSUMED:Status.RELEASED); } }
}
