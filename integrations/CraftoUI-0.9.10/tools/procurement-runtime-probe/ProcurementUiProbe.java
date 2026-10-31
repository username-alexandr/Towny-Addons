package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import com.google.gson.*;
import com.palmergames.bukkit.towny.*;

/** Real CivicWorkbench.handle JSON paths; offline players and deterministic Towny bank inherited explicitly. */
public final class ProcurementUiProbe extends ProcurementProbe {
    Object workbench,state;Method handle;Player user;JsonObject last;String clientToken="";int sequence;JsonObject usedConfirmation;
    @Override void setup()throws Exception{phase=Files.exists(proof.resolve("first-passed.txt"))?"restart":"first";super.setup();}
    // Repository snapshot reads may be already completed; they are not the asynchronous public API.
    <V>void observe(java.util.concurrent.CompletionStage<V> future,Consumer<V> next){future.whenComplete((value,error)->{if(error!=null)fail(error);else later(1,()->next.accept(value));});}
    static Object hidden(Object o,String name)throws Exception{return field(o,name);}
    static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    @Override void afterBank()throws Exception{
        var ui=Bukkit.getPluginManager().getPlugin("CraftoUI");check(ui!=null&&ui.isEnabled()&&ui.getDescription().getVersion().equals("0.9.0-dev"),"actual CraftoUI planner plugin enabled");
        workbench=hidden(ui,"civicWorkbench");Class<?> stateType=Class.forName("cc.crafto.ui.server.CivicWorkbench$State",true,ui.getClass().getClassLoader());var constructor=stateType.getDeclaredConstructor();constructor.setAccessible(true);state=constructor.newInstance();
        handle=workbench.getClass().getDeclaredMethod("handle",Player.class,stateType,JsonObject.class,Consumer.class);handle.setAccessible(true);user=phase.equals("first")?mayor:next;
        if(phase.equals("restart")){resumeUi();return;}
        openOffers("forge",()->{
            check(withdrawals==0&&contracts.active(T).isEmpty(),"opening actual planner supply and provider offers never spends money");
            click("procurement-quote",null,()->{
                published=UUID.fromString(last.get("id").getAsString());observe(procurement.repository().get(published),order->{try{
                    firstCents=order.cents();check(order.state().equals("QUOTE")&&withdrawals==0,"UI selection persists an exact quote without paying it");
                    click("procurement-review-pay",published.toString(),()->{
                        check(last.get("op").getAsString().equals("review")&&last.get("lines").toString().contains("городской казны"),"UI opens separate confirmation with city funding source and exact terms");
                        check(last.get("token").getAsString().equals(clientToken),"asynchronous review retains the existing client token used by unchanged client");
                        String confirmation=last.get("confirmation").getAsString();permission=false;
                        send("confirm",Map.of("confirmation",confirmation),()->{
                            check(last.get("op").getAsString().equals("error")&&withdrawals==0,"permission revoked after review prevents payment");permission=true;
                            send("refresh",Map.of(),()->pay(published,()->poll(published,"FUNDED",100,()->publishedFunded())));
                        });
                    });
                }catch(Exception e){fail(e);}});
            });
        });
    }
    void publishedFunded()throws Exception{
        check(withdrawals==1&&balance()==100000-firstCents/100.0&&contracts.active(T).isEmpty(),"UI funding acknowledged one exact debit before contract publication");
        pay(published,()->{
            check(contracts.active(T).size()==1&&withdrawals==1&&last.get("lines").toString().contains("Опубликован"),"second confirmed UI step publishes backed contract without another debit");
            check(last.get("rows").toString().contains("Заказ уже опубликован"),"published order explains why full refund is no longer available here");
            var repeated=usedConfirmation.deepCopy();invoke(repeated,()->{
                check(last.get("op").getAsString().equals("error")&&withdrawals==1&&contracts.active(T).size()==1,"replayed network-shaped confirmation cannot duplicate debit or contract");createReturn();
            });
        });
    }
    void createReturn()throws Exception{
        openOffers("town_hall",()->click("procurement-quote",null,()->{
            refunded=UUID.fromString(last.get("id").getAsString());pay(refunded,()->poll(refunded,"FUNDED",100,()->{
                click("procurement-review-cancel",refunded.toString(),()->{
                    check(last.get("op").getAsString().equals("review")&&last.get("lines").toString().contains("Возврат оплаченного резерва"),"paid cancellation requires a distinct refund review");
                    confirm(()->poll(refunded,"REFUNDED",100,()->{
                        check(withdrawals==2&&deposits==1&&balance()==100000-firstCents/100.0,"UI cancellation returns exactly the funded reserve once");
                        check(last.get("rows").toString().contains("Резерв возвращён"),"completed refund is visible and no further refund action is offered");createSaved();
                    }));
                });
            }));
        }));
    }
    void createSaved()throws Exception{
        openOffers("market",()->click("procurement-quote",null,()->{
            pending=UUID.fromString(last.get("id").getAsString());observe(procurement.repository().get(pending),order->{try{
                pendingCents=order.cents();pay(pending,()->poll(pending,"FUNDED",100,()->{
                    town.setMayor(TownyAPI.getInstance().getResident(N));town.save();send("refresh",Map.of(),()->{
                        check(last.get("op").getAsString().equals("error")&&withdrawals==3&&contracts.active(T).size()==1,"mayor replacement revokes UI access without spending or releasing the paid reserve");
                        Files.writeString(proof.resolve("expected.txt"),published+"\n"+refunded+"\n"+pending+"\n"+firstCents+"\n"+pendingCents+"\n");finish();
                    });
                }));
            }catch(Exception e){fail(e);}});
        }));
    }
    void resumeUi()throws Exception{
        var expected=Files.readAllLines(proof.resolve("expected.txt"));published=UUID.fromString(expected.get(0));refunded=UUID.fromString(expected.get(1));pending=UUID.fromString(expected.get(2));firstCents=Long.parseLong(expected.get(3));pendingCents=Long.parseLong(expected.get(4));
        check(contracts.active(T).size()==1&&balance()==100000-(firstCents+pendingCents)/100.0&&withdrawals==0&&deposits==0,"full JVM restart preserves paid and published receipts without bank replay");
        send("open",Map.of("route","planner"),()->click("procurement-page","1",()->{
            check(last.get("rows").toString().contains("ожидает публикации")&&withdrawals==0,"new mayor finds funded request from empty session plan via city purchases");
            click("procurement-order",pending.toString(),()->pay(pending,()->{
                check(contracts.active(T).size()==2&&withdrawals==0&&last.get("lines").toString().contains("Опубликован"),"new mayor explicitly publishes preserved funding through UI without debit replay");
                send("open",Map.of("route","planner"),()->click("procurement-page","1",()->click("procurement-order",refunded.toString(),()->{
                    check(last.get("lines").toString().contains("Резерв возвращён")&&deposits==0,"refund receipt and disabled financial actions survive full restart");finish();
                })));
            }));
        }));
    }
    @SuppressWarnings("unchecked")void openOffers(String project,Work next)throws Exception{
        // The selected plan is an explicit server-side fixture, not an untrusted client action.
        var goals=(Map<String,Integer>)hidden(state,"plan");goals.clear();goals.put(project,1);set(state,"planTown",T);
        send("open",Map.of("route","planner"),()->click("route","planner-supply",()->click("procurement-offers",project,next)));
    }
    void pay(UUID id,Work done)throws Exception{click("procurement-review-pay",id.toString(),()->confirm(done));}
    void confirm(Work done)throws Exception{check(last.get("op").getAsString().equals("review"),"financial action awaits real server-owned review ticket");send("confirm",Map.of("confirmation",last.get("confirmation").getAsString()),done);}
    void poll(UUID id,String wanted,int tries,Work done)throws Exception{
        send("refresh",Map.of(),()->observe(procurement.repository().get(id),row->{try{if(row.state().equals(wanted)){done.run();return;}if(tries<=0)throw new AssertionError("UI receipt timeout: "+row.state()+" wanted "+wanted);later(2,()->poll(id,wanted,tries-1,done));}catch(Exception e){fail(e);}}));
    }
    void send(String op,Map<String,?> values,Work next)throws Exception{var request=new JsonObject();request.addProperty("op",op);request.addProperty("request",++sequence);request.addProperty("size",12);request.addProperty("token",clientToken);values.forEach((k,v)->request.add(k,new Gson().toJsonTree(v)));if(op.equals("confirm"))usedConfirmation=request.deepCopy();invoke(request,next);}
    void invoke(JsonObject request,Work next)throws Exception{
        handle.invoke(workbench,user,state,request,(Consumer<JsonObject>)response->{
            if(response.get("op").getAsString().equals("notification"))return;
            last=response.deepCopy();if(response.get("op").getAsString().equals("view"))clientToken=response.get("token").getAsString();
            try{Files.writeString(proof.resolve(phase+"-ui.jsonl"),response+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception error){fail(error);return;}
            later(1,next);
        });
    }
    @SuppressWarnings("unchecked")void click(String kind,String id,Work next)throws Exception{
        if(!last.get("op").getAsString().equals("view"))throw new AssertionError("Expected view before "+kind+": "+last);
        var actions=(Map<String,Object>)hidden(state,"actions");
        for(var raw:last.getAsJsonArray("rows")){var row=raw.getAsJsonObject();String key=row.get("action").getAsString();Object action=actions.get(key);if(action==null)continue;
            var k=action.getClass().getDeclaredMethod("kind");k.setAccessible(true);var i=action.getClass().getDeclaredMethod("id");i.setAccessible(true);
            if(kind.equals(k.invoke(action))&&(id==null||id.equals(i.invoke(action)))){send("act",Map.of("action",key),next);return;}
        }
        int page=last.get("page").getAsInt();if(page+1<last.get("pages").getAsInt()){send("page",Map.of("page",page+1),()->click(kind,id,next));return;}
        throw new AssertionError("Visible enabled action absent "+kind+" "+id+": "+last);
    }
    @Override void finish()throws Exception{
        var values=new Properties();balances.forEach((k,v)->values.setProperty(k.toString(),v.toString()));try(var out=Files.newOutputStream(proof.resolve("bank.properties"))){values.store(out,"Deterministic Towny bank fixture, no live Vault claimed");}
        TownySettings.setProperty("economy.using_economy",false);TownyUniverse.getInstance().getDataSource().saveAll();Files.writeString(proof.resolve(phase+"-passed.txt"),"PASS "+checks+" assertions; actual CraftoUI CivicWorkbench JSON actions, Folia/Towny/Builds/Contracts/Treasury, explicit confirmation, permission and mayor change, replay, exact funding/refund and full JVM restart; server-side plan seed, offline Player/EntityScheduler and deterministic economy substitutes, no rendered network client or live Vault claimed\n");Bukkit.getGlobalRegionScheduler().run(this,t->Bukkit.shutdown());
    }
}
