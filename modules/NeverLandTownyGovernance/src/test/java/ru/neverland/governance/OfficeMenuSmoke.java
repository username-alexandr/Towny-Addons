package ru.neverland.governance;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import ru.neverland.governance.gui.GovernanceMenuHolder;
import ru.neverland.governance.gui.OfficeConfirmation;
import ru.neverland.governance.model.OfficeDefinition;
import ru.neverland.governance.model.OfficeHolder;

public final class OfficeMenuSmoke {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args){
        UUID town=UUID.randomUUID(),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var office=new OfficeDefinition("treasurer","Казначей",Material.CHEST,"",1,true,Set.of(),false,List.of("Бюджет"));
        var seats=new ArrayList<OfficeHolder>();
        var quote=new OfficeConfirmation(town,actor,target,false,office,seats,100);
        check(quote.current(town,actor,office,List.of(),101),"unchanged confirmation accepted");
        seats.add(new OfficeHolder(target,"Житель",1,actor));
        check(quote.holders().isEmpty(),"confirmation owns an immutable seat snapshot");
        check(!quote.current(UUID.randomUUID(),actor,office,List.of(),101),"another town rejected");
        check(!quote.current(town,UUID.randomUUID(),office,List.of(),101),"another viewer rejected");
        check(!quote.current(town,actor,office,seats,101),"new appointment invalidates old review");
        check(!quote.current(town,actor,null,List.of(),101),"removed office invalidates old review");
        var changed=new OfficeDefinition("treasurer","Казначей",Material.CHEST,"",2,true,Set.of(),false,List.of("Бюджет"));
        check(!quote.current(town,actor,changed,List.of(),101),"changed office definition invalidates review");
        check(!quote.current(town,actor,office,List.of(),60_000_000_100L),"expired confirmation rejected");
        check(!quote.current(town,actor,office,List.of(),99),"invalid monotonic time rejected");
        var dismissal=new OfficeConfirmation(town,actor,target,true,office,seats,100);
        check(!dismissal.current(town,actor,office,List.of(new OfficeHolder(target,"Житель",2,actor)),101),"dismissal cannot remove a replacement appointment");
        var holder=new GovernanceMenuHolder(GovernanceMenuHolder.Type.OFFICE_CONFIRM,town,actor);AtomicInteger calls=new AtomicInteger();holder.action(29,p->calls.incrementAndGet());
        check(holder.takeAction(8)==null,"non-action slot has no command");
        holder.takeAction(29).accept(null);check(calls.get()==1,"first click executes once");
        check(holder.takeAction(29)==null,"second click rejected");
        holder.action(33,p->calls.incrementAndGet());check(holder.takeAction(33)==null,"second transition in same inventory rejected");
        System.out.println("Office menu: "+checks+" assertions passed");
    }
}
