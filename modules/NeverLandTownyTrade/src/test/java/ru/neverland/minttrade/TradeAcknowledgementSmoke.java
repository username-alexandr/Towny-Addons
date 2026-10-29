package ru.neverland.minttrade;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import ru.neverland.minttrade.model.*;
import ru.neverland.minttrade.service.*;
import ru.neverland.minttrade.guarantee.GuaranteeTerms;

public final class TradeAcknowledgementSmoke {
    static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    static <T>T await(CompletionStage<T> f)throws Exception{return f.toCompletableFuture().get(5,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("trade-ack-");UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();var repository=new TradeRepository(folder);repository.load();
        var old=new TradeOffer(UUID.randomUUID(),seller,buyer,"stone",1,999999);await(repository.putOffer(old));check(!repository.findOffer(old.id().toString()).guarantee().enabled(),"old offer remains uninsured");
        var terms=new GuaranteeTerms(2000,100,60000);var next=new TradeOffer(UUID.randomUUID(),seller,buyer,"iron",2,999999,terms,10000,"a".repeat(64));var writer=CaravanEffectsSmoke.lock(folder.resolve("trade-data.yml"));writer.lock();CompletionStage<Void> commit;CompletionStage<Void> closing;
        try{commit=repository.putOffer(next);try(var guard=SuiteState.guard()){check(repository.findOffer(next.id().toString())==null&&!commit.toCompletableFuture().isDone(),"delayed file does not publish an offer or block Suite");check(repository.busy(),"busy gate stops overlapping agreements");closing=repository.prepareClose();check(!closing.toCompletableFuture().isDone(),"shutdown waits for accepted snapshot");}}finally{writer.unlock();}
        await(commit);await(closing);var restarted=new TradeRepository(folder);restarted.load();var restored=restarted.findOffer(next.id().toString());check(restored.equals(next),"restart preserves exact frozen quote and guarantee");check(restarted.findOffer(old.id().toString()).equals(old),"legacy terms survive additive migration");
        await(restarted.setTariff(seller,4));check(restarted.tariff(seller,0)==4,"tariff published after acknowledgement");await(restarted.removeOffer(next));check(restarted.findOffer(next.id().toString())==null&&restarted.findOffer(old.id().toString())!=null,"cancel only selected offer");await(restarted.prepareClose());
        var third=new TradeRepository(folder);third.load();check(third.tariff(seller,0)==4&&third.findOffer(next.id().toString())==null,"tariff/cancel survive another restart");await(third.prepareClose());
        byte[] before=Files.readAllBytes(folder.resolve("trade-data.yml"));var damaged=new TradeRepository(folder);Files.writeString(folder.resolve("trade-data.yml"),"schema: 99\n");boolean rejected=false;try{damaged.load();}catch(RuntimeException expected){rejected=true;}check(rejected,"unknown schema refused");await(damaged.prepareClose());check(Files.readString(folder.resolve("trade-data.yml")).equals("schema: 99\n"),"failed load shutdown never replaces source with empty data");Files.write(folder.resolve("trade-data.yml"),before);
        System.out.println("TradeAcknowledgementSmoke PASS "+checks+" checks");
    }
}
