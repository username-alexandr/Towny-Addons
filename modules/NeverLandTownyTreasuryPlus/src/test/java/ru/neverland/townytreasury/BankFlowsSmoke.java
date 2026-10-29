package ru.neverland.townytreasury;
import java.util.*;import ru.neverland.townytreasury.service.BankFlows;import ru.neverland.townytreasury.model.Money;
public final class BankFlowsSmoke{
 static int count;static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 public static void main(String[] args){UUID town=UUID.randomUUID(),other=UUID.randomUUID(),operation=UUID.randomUUID();var rows=new BankFlows(3);
  check(rows.add(town,1,100,false,"x".repeat(10000),operation),"first observer event fits bounded buffer");check(rows.add(town,1,100,false,"same timestamp",operation),"equal bank events remain distinct");check(rows.add(other,1,50,true,"deposit",null),"other town flow accepted");
  check(rows.withdrawn(town)==200&&rows.deposited(other)==50&&rows.batch(f->true,10).get(0).reason().length()==1024,"aggregate balances and metadata bounded");var batch=rows.batch(f->f.town().equals(town),1);rows.remove(batch);check(rows.withdrawn(town)==100&&rows.size()==2,"ACK removes only selected exact event");
  rows.add(town,1,Money.MAX,false,"large",null);check(rows.withdrawn(town)==Money.MAX,"aggregate saturates conservatively without overflow");check(!rows.add(town,1,1,true,"overflow",null)&&rows.gap()&&rows.size()==3,"overflow closes buffer without allocating unbounded records");rows.remove(rows.batch(f->true,3));check(!rows.add(town,2,1,true,"retry",null)&&rows.size()==0,"draining does not silently clear the accounting gap");
  var exact=new BankFlows(4);exact.add(town,1,20,false,"known",operation);exact.add(town,1,25,false,"external",null);exact.add(town,1,30,true,"known refund",operation);exact.removeOperation(operation);check(exact.size()==1&&exact.withdrawn(town)==25&&exact.deposited(town)==0,"exact receipt removal preserves unrelated movements");System.out.println("BankFlowsSmoke PASS "+count+" checks");
 }
}
