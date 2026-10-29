package ru.neverland.townyspecialization.production;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import static ru.neverland.townyspecialization.production.ProductionRepository.*;

public final class ProductionSmoke {
    static int checks;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static <T>T done(CompletionStage<T> stage)throws Exception{return stage.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    @FunctionalInterface interface Work{void run()throws Exception;}
    static void denied(Work work,String why)throws Exception{try{work.run();throw new AssertionError("accepted: "+why);}catch(IllegalArgumentException|ExecutionException expected){checks++;}}
    static Map<String,Object> source(Batch b,String state){return Map.of("id",b.contract(),"title",b.label(),"scope","CITY","ownerTown",b.town().toString(),"unlock",b.unlock(),"minimumCredit",1L,"needs",b.materials(),"credited",state.equals("COMPLETED")?b.materials():Map.of(),"funded",state.equals("COMPLETED"),"state",state);}
    static Batch produce(ProductionRepository repo,UUID town,ProductionCatalog.Product product,int amount,ProductionCatalog.Limits limits)throws Exception{Batch b=done(repo.createBatch(UUID.randomUUID(),town,product,amount,"Mayor",limits));return done(repo.acknowledge(b.id(),source(b,"COMPLETED"),"Mayor"));}
    static long available(ProductionRepository repo,UUID town,String product)throws Exception{return done(repo.stock(town)).stream().filter(s->s.product().equals(product)).mapToLong(Stock::available).sum();}
    public static void main(String[] args)throws Exception{
        var y=new YamlConfiguration();try(var input=ProductionSmoke.class.getResourceAsStream("/production.yml")){if(input==null)throw new AssertionError("missing resource");y.load(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));}
        var catalog=ProductionCatalog.load(y);check(catalog.products().size()==7&&catalog.projects().size()==3,"seven bounded paid recipes and three actual shared goals");
        var limits=catalog.limits();Path file=Files.createTempDirectory("production-smoke-").resolve("production.sqlite");var repo=new ProductionRepository(file);done(repo.ready());UUID a=UUID.randomUUID(),b=UUID.randomUUID();var farm=catalog.products().get("farm_provisions");var port=catalog.products().get("port_equipment");
        var pending=done(repo.createBatch(UUID.randomUUID(),a,farm,2,"MayorA",limits));check(done(repo.stock(a)).isEmpty(),"intent alone creates no product");check(pending.materials().equals(Map.of("WHEAT",256L,"CARROT",128L)),"recipe scales exact actual input quantities");
        check(done(repo.createBatch(pending.id(),a,farm,2,"RenamedMayor",limits)).id().equals(pending.id()),"repeated same request keeps one fixed recipe");
        final var initialRepo=repo;denied(()->done(initialRepo.createBatch(pending.id(),a,farm,1,"MayorA",limits)),"same ID different recipe refused");
        var foreign=new HashMap<>(source(pending,"COMPLETED"));foreign.put("ownerTown",b.toString());denied(()->done(initialRepo.acknowledge(pending.id(),foreign,"MayorA")),"foreign completed source receipt cannot produce goods");check(done(repo.stock(a)).isEmpty(),"foreign receipt cannot create even partial output");
        var partial=new HashMap<>(source(pending,"COMPLETED"));partial.put("credited",Map.of("WHEAT",1L));denied(()->done(initialRepo.acknowledge(pending.id(),partial,"MayorA")),"incomplete spent materials refused");
        var opened=done(repo.acknowledge(pending.id(),source(pending,"OPEN"),"MayorA"));check(opened.state().equals("OPEN")&&done(repo.stock(a)).isEmpty(),"funding intent not equivalent to materials consumed");
        done(repo.acknowledge(pending.id(),source(pending,"COMPLETED"),"MayorA"));done(repo.acknowledge(pending.id(),source(pending,"COMPLETED"),"MayorA"));check(available(repo,a,farm.id())==2&&done(repo.stock(a)).get(0).produced()==2,"one exact source receipt issues one batch only");
        var saved=pending;repo.closeAsync().toCompletableFuture().get(10,TimeUnit.SECONDS);repo=new ProductionRepository(file);done(repo.ready());check(available(repo,a,farm.id())==2&&done(repo.batch(saved.id())).state().equals("ISSUED"),"full repository restart retains products and deduplication state");done(repo.acknowledge(saved.id(),source(saved,"COMPLETED"),"MayorA"));check(available(repo,a,farm.id())==2,"replayed source after restart cannot issue again");
        produce(repo,b,port,2,limits);var goal=catalog.projects().get("common_supply");UUID project=UUID.randomUUID();var created=done(repo.createProject(project,a,goal,catalog.products(),"MayorA",limits));check(created.members().isEmpty()&&!created.funded(),"joint goal has real unsatisfied requirements");
        UUID receiptA=UUID.randomUUID();var contributed=done(repo.contribute(receiptA,project,a,"agricultural",farm.id(),2,"MayorA"));check(available(repo,a,farm.id())==0&&done(repo.stock(a)).get(0).reserved()==2&&!contributed.funded(),"own existing goods moved into one actual project reserve");
        done(repo.contribute(receiptA,project,a,"agricultural",farm.id(),2,"MayorA"));check(done(repo.stock(a)).get(0).reserved()==2,"repeated contribution receipt cannot double-reserve");
        final var joinedRepo=repo;denied(()->done(joinedRepo.contribute(receiptA,project,a,"agricultural",farm.id(),1,"MayorA")),"contribution receipt terms immutable");
        denied(()->done(joinedRepo.finishProject(project,a,false,"MayorA","Detailed completion check")),"one direction cannot complete joint goal");
        produce(repo,a,port,1,limits);denied(()->done(joinedRepo.contribute(UUID.randomUUID(),project,a,"port",port.id(),1,"MayorA")),"same city switching direction cannot impersonate a second participant");
        var full=done(repo.contribute(UUID.randomUUID(),project,b,"port",port.id(),2,"MayorB"));check(full.funded()&&full.members().size()==2,"second actual city supplies second direction");
        denied(()->done(joinedRepo.finishProject(project,b,false,"MayorB","Detailed completion check")),"only organizer city can finish");
        var finished=done(repo.finishProject(project,a,false,"MayorA","Detailed completion check"));check(finished.project().state().equals("COMPLETED")&&finished.project().result().equals(goal.result()),"durable shared result contains exact frozen goal");
        check(done(repo.stock(a)).stream().filter(s->s.product().equals(farm.id())).findFirst().orElseThrow().reserved()==0&&available(repo,b,port.id())==0,"completion consumes reserved goods with no new stock or cash");
        done(repo.finishProject(project,a,false,"MayorA","Detailed completion check"));denied(()->done(joinedRepo.finishProject(project,a,true,"MayorA","Cannot reverse consumed stock")),"completed goal cannot refund spent goods");
        produce(repo,a,farm,2,limits);produce(repo,b,port,2,limits);UUID cancellation=UUID.randomUUID();done(repo.createProject(cancellation,a,goal,catalog.products(),"MayorA",limits));done(repo.contribute(UUID.randomUUID(),cancellation,a,"agricultural",farm.id(),2,"MayorA"));done(repo.contribute(UUID.randomUUID(),cancellation,b,"port",port.id(),1,"MayorB"));done(repo.finishProject(cancellation,a,true,"MayorA","Cancelled without player losses"));done(repo.finishProject(cancellation,a,true,"MayorA","Cancelled without player losses"));check(available(repo,a,farm.id())==2&&available(repo,b,port.id())==2,"cancellation returns exact original goods once to each owner");
        var narrow=new ProductionCatalog.Limits(1,1,1,1,1,1,1);Path capFile=Files.createTempDirectory("production-limits-").resolve("db.sqlite");var cap=new ProductionRepository(capFile);done(cap.ready());var held=done(cap.createBatch(UUID.randomUUID(),a,farm,1,"Mayor",narrow));denied(()->done(cap.createBatch(UUID.randomUUID(),b,port,1,"Other",narrow)),"server and city pending limits account promised capacity");done(cap.acknowledge(held.id(),source(held,"COMPLETED"),"Mayor"));denied(()->done(cap.createBatch(UUID.randomUUID(),b,port,1,"Other",narrow)),"stored goods cap remains after pending becomes issued");done(cap.closeAsync());
        var cancelled=done(repo.createBatch(UUID.randomUUID(),a,farm,1,"MayorA",limits));done(repo.acknowledge(cancelled.id(),source(cancelled,"CANCELLED"),"MayorA"));check(available(repo,a,farm.id())==2,"cancelled source creates no products and does not affect older ones");
        denied(()->done(joinedRepo.acknowledge(cancelled.id(),source(cancelled,"COMPLETED"),"MayorA")),"contradictory source completion after cancellation refused");
        var faultBatch=done(repo.createBatch(UUID.randomUUID(),a,farm,1,"MayorA",limits));try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=connection.createStatement()){statement.execute("CREATE TRIGGER fail_batch_issue BEFORE UPDATE OF state ON batches WHEN NEW.state='ISSUED' BEGIN SELECT RAISE(ABORT,'fixture persistence failure'); END");}
        denied(()->done(joinedRepo.acknowledge(faultBatch.id(),source(faultBatch,"COMPLETED"),"MayorA")),"real failure between stock credit and state update rolls back both");done(repo.closeAsync());
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=connection.createStatement()){statement.execute("DROP TRIGGER fail_batch_issue");}
        repo=new ProductionRepository(file);done(repo.ready());check(available(repo,a,farm.id())==2&&done(repo.batch(faultBatch.id())).state().equals("PLANNING"),"SQL fault cannot leave free products or false issued receipt");done(repo.acknowledge(faultBatch.id(),source(faultBatch,"COMPLETED"),"MayorA"));check(available(repo,a,farm.id())==3,"known completed physical receipt can reconcile once after storage repair");
        check(!done(repo.history(a,1)).isEmpty()&&done(repo.joint(project)).project().state().equals("COMPLETED"),"history and finished shared result survive all restarts");
        done(repo.closeAsync());System.out.println("ProductionSmoke PASS "+checks+" checks; real SQLite backing, unique source receipts, reservations, multi-city goals, refunds, limits, rollback and restart");
    }
}
