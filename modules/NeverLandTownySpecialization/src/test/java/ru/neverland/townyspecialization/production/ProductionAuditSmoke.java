package ru.neverland.townyspecialization.production;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
public final class ProductionAuditSmoke {
 public static void main(String[]args)throws Exception{
  var repo=new ProductionRepository(Files.createTempDirectory("production-audit-").resolve("db.sqlite"));ProductionSmoke.done(repo.ready());var product=new ProductionCatalog.Product("profile_goods","trade","Торговая партия","PAPER",Map.of("PAPER",1L));var limits=new ProductionCatalog.Limits(2,2,1,2,2,1,1);UUID town=UUID.randomUUID();var batch=ProductionSmoke.done(repo.createBatch(UUID.randomUUID(),town,product,1,"Mayor",limits));
  var original=ProductionSmoke.done(repo.auditPending()).get(0);ProductionSmoke.check(original.kind().equals("CITY_PROJECT")&&original.actor().name().equals("Mayor"),"canonical operation creates immutable admin evidence");
  var broken=new ProductionAudit(repo,row->false,message->{});ProductionSmoke.denied(()->ProductionSmoke.done(broken.flush()),"failed admin projection retains receipt");ProductionSmoke.check(ProductionSmoke.done(repo.auditPending()).get(0).equals(original),"retry retains exact ID and original timestamp");ProductionSmoke.denied(()->ProductionSmoke.done(broken.closeAsync()),"shutdown reports outstanding projection rather than silently dropping it");
  var enter=new CountDownLatch(1);var release=new CountDownLatch(1);var projected=new ArrayList<AuditRecord>();var audit=new ProductionAudit(repo,row->{enter.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("audit gate");}catch(InterruptedException e){throw new RuntimeException(e);}projected.add(row);return true;},message->{});var flush=audit.flush();if(!enter.await(5,TimeUnit.SECONDS))throw new AssertionError("writer never entered");ProductionSmoke.check(SuiteState.tryRun("native audit delayed",()->{}),"blocked admin writer does not own SuiteState");ProductionSmoke.check(ProductionSmoke.done(repo.batch(batch.id())).id().equals(batch.id()),"blocked admin writer does not occupy SQLite worker");release.countDown();ProductionSmoke.done(flush);ProductionSmoke.check(ProductionSmoke.done(repo.auditPending()).isEmpty()&&projected.equals(List.of(original)),"acknowledged exact receipt leaves durable outbox once");ProductionSmoke.done(audit.closeAsync());ProductionSmoke.done(repo.closeAsync());System.out.println("ProductionAuditSmoke PASS "+ProductionSmoke.checks+" checks; failure, immutable retry, free Suite and SQL worker, durable ACK");
 }
}
