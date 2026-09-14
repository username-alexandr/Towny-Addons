package ru.neverland.townyjustice;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
/** Cases and their payment intents are committed in one atomic YAML snapshot. */
public final class JusticeRepository implements JusticePayments.Store {
    public record State(Map<UUID,JusticeCase> cases,Map<UUID,JusticePayment> payments,Map<UUID,JusticePrison> prisons){
        public State{cases=Map.copyOf(cases);payments=Map.copyOf(payments);prisons=Map.copyOf(prisons);
            for(var e:cases.entrySet()){var c=e.getValue();if(!e.getKey().equals(c.id()))throw new IllegalArgumentException("ID дела не совпадает");for(var id:java.util.stream.Stream.of(c.payment(),c.payout()).filter(Objects::nonNull).toList()){var p=payments.get(id);if(p==null||!p.caseId().equals(c.id())||!p.town().equals(c.town())||p.amount()!=c.amount())throw new IllegalArgumentException("Дело без связанного платежа");}
                var funding=c.payment()==null?null:payments.get(c.payment());var settlement=c.payout()==null?null:payments.get(c.payout());
                if(c.kind()==JusticeCase.Kind.FINE){
                    if(Set.of(JusticeCase.Phase.PAYING,JusticeCase.Phase.PAID).contains(c.phase())&&funding==null||Set.of(JusticeCase.Phase.OPEN,JusticeCase.Phase.CANCELLED).contains(c.phase())&&funding!=null||c.phase()==JusticeCase.Phase.PAID&&funding.step()!=JusticePayment.Step.COMPLETE)throw new IllegalArgumentException("Состояние штрафа не подтверждено платежом");
                }else if(c.amount()==0){if(funding!=null||settlement!=null||Set.of(JusticeCase.Phase.FUNDING,JusticeCase.Phase.REFUNDING).contains(c.phase()))throw new IllegalArgumentException("У бесплатного розыска нет денежной операции");
                }else{
                    if(funding==null)throw new IllegalArgumentException("Нет резерва награды");
                    if(c.phase()!=JusticeCase.Phase.FUNDING&&c.phase()!=JusticeCase.Phase.CANCELLED&&funding.step()!=JusticePayment.Step.COMPLETE)throw new IllegalArgumentException("Розыск без подтверждённого резерва");
                    if((c.capturedAt()>0||c.phase()==JusticeCase.Phase.REFUNDING)&&settlement==null)throw new IllegalArgumentException("Не сохранено назначение резерва");
                    if(c.phase()==JusticeCase.Phase.CANCELLED&&funding.step()!=JusticePayment.Step.CANCELLED&&(funding.step()!=JusticePayment.Step.COMPLETE||settlement==null||settlement.step()!=JusticePayment.Step.COMPLETE))throw new IllegalArgumentException("Отмена без возврата резерва");
                    if(settlement!=null&&(funding.step()!=JusticePayment.Step.COMPLETE||!Set.of(JusticeCase.Phase.JAILED,JusticeCase.Phase.RELEASING,JusticeCase.Phase.RELEASED,JusticeCase.Phase.REFUNDING,JusticeCase.Phase.CANCELLED).contains(c.phase())))throw new IllegalArgumentException("Выплата без завершённого основания");
                }
                if(c.payment()!=null){var p=payments.get(c.payment());if(p.kind()!=(c.kind()==JusticeCase.Kind.FINE?JusticePayment.Kind.FINE:JusticePayment.Kind.BOUNTY_FUND)||!p.resident().equals(c.subject()))throw new IllegalArgumentException("Неверный плательщик");}
                if(c.payout()!=null){var p=payments.get(c.payout());if(c.kind()!=JusticeCase.Kind.WARRANT||p.kind()!=(c.capturedAt()>0?JusticePayment.Kind.BOUNTY_PAY:JusticePayment.Kind.BOUNTY_REFUND)||!p.resident().equals(c.capturedAt()>0?c.hunter():c.subject()))throw new IllegalArgumentException("Неверное назначение резерва");}
            }
            for(var e:payments.entrySet()){var p=e.getValue();var c=cases.get(p.caseId());if(!e.getKey().equals(p.id())||c==null||!p.town().equals(c.town())||p.amount()!=c.amount())throw new IllegalArgumentException("Платёж без дела");
                if(!p.id().equals(c.payment())&&!p.id().equals(c.payout())&&!(p.kind()==JusticePayment.Kind.FINE&&c.kind()==JusticeCase.Kind.FINE&&p.step()==JusticePayment.Step.CANCELLED&&p.resident().equals(c.subject())))throw new IllegalArgumentException("Лишний платёж без назначения");
            }
            for(var e:prisons.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("ID тюрьмы не совпадает");
        }
    }
    private final Path file;private State state=new State(Map.of(),Map.of(),Map.of());private boolean loaded;
    public JusticeRepository(Path file){this.file=file;}public State state(){return state;}public boolean writable(){return loaded&&AtomicFiles.writable(file);}
    @Override public JusticePayment get(UUID id){return state.payments().get(id);}
    @Override public void put(JusticePayment p)throws IOException{var values=new HashMap<>(state.payments());values.put(p.id(),p);commit(new State(state.cases(),values,state.prisons()));}
    public void put(JusticeCase c,JusticePayment p)throws IOException{var cases=new HashMap<>(state.cases());cases.put(c.id(),c);var values=new HashMap<>(state.payments());if(p!=null)values.put(p.id(),p);commit(new State(cases,values,state.prisons()));}
    public void prison(JusticePrison p)throws IOException{var values=new HashMap<>(state.prisons());values.put(p.id(),p);commit(new State(state.cases(),state.payments(),values));}
    public void load()throws IOException{loaded=false;var cases=new LinkedHashMap<UUID,JusticeCase>();var payments=new LinkedHashMap<UUID,JusticePayment>();var prisons=new LinkedHashMap<UUID,JusticePrison>();
        if(Files.exists(file))try{var y=new YamlConfiguration();y.load(file.toFile());if(n(y,"schema")!=1)throw new IOException("Неверная схема суда");
            var root=section(y,"payments");for(String key:root.getKeys(false)){var p=section(root,key);UUID id=UUID.fromString(key);payments.put(id,new JusticePayment(id,uuid(p,"case"),uuid(p,"town"),uuid(p,"resident"),JusticePayment.Kind.valueOf(s(p,"kind")),n(p,"amount"),JusticePayment.Step.valueOf(s(p,"step")),n(p,"created"),n(p,"check")));}
            root=section(y,"prisons");for(String key:root.getKeys(false)){var p=section(root,key);UUID id=UUID.fromString(key);if(!p.isBoolean("ready"))throw new IOException("Нет состояния тюрьмы");prisons.put(id,new JusticePrison(id,uuid(p,"town"),uuid(p,"world"),Math.toIntExact(n(p,"x")),Math.toIntExact(n(p,"y")),Math.toIntExact(n(p,"z")),s(p,"name"),p.getBoolean("ready")));}
            root=section(y,"cases");for(String key:root.getKeys(false)){var p=section(root,key);UUID id=UUID.fromString(key);cases.put(id,new JusticeCase(id,uuid(p,"town"),uuid(p,"subject"),uuid(p,"issuer"),JusticeCase.Kind.valueOf(s(p,"kind")),n(p,"amount"),Math.toIntExact(n(p,"hours")),s(p,"reason"),n(p,"created"),n(p,"due"),JusticeCase.Phase.valueOf(s(p,"phase")),optional(p,"payment"),optional(p,"payout"),optional(p,"hunter"),optional(p,"prison"),n(p,"capture-started"),n(p,"captured-at")));}
            state=new State(cases,payments,prisons);
        }catch(Exception e){throw new IOException("justice-data.yml повреждён; исходный файл сохранён",e);}
        else state=new State(cases,payments,prisons);AtomicFiles.loaded(file);loaded=true;
    }
    public void commit(State next)throws IOException{if(!writable())throw new IOException("Запись суда остановлена до успешной загрузки базы");if(next.equals(state))return;
        AtomicFiles.write(file,()->{var y=new YamlConfiguration();y.set("schema",1);y.createSection("cases");y.createSection("payments");y.createSection("prisons");
            for(var c:next.cases().values()){var p=y.createSection("cases."+c.id());p.set("town",c.town().toString());p.set("subject",c.subject().toString());p.set("issuer",c.issuer().toString());p.set("kind",c.kind().name());p.set("amount",c.amount());p.set("hours",c.hours());p.set("reason",c.reason());p.set("created",c.created());p.set("due",c.due());p.set("phase",c.phase().name());p.set("payment",value(c.payment()));p.set("payout",value(c.payout()));p.set("hunter",value(c.hunter()));p.set("prison",value(c.prison()));p.set("capture-started",c.captureStarted());p.set("captured-at",c.capturedAt());}
            for(var t:next.payments().values()){var p=y.createSection("payments."+t.id());p.set("case",t.caseId().toString());p.set("town",t.town().toString());p.set("resident",t.resident().toString());p.set("kind",t.kind().name());p.set("amount",t.amount());p.set("step",t.step().name());p.set("created",t.created());p.set("check",t.check());}
            for(var v:next.prisons().values()){var p=y.createSection("prisons."+v.id());p.set("town",v.town().toString());p.set("world",v.world().toString());p.set("x",v.x());p.set("y",v.y());p.set("z",v.z());p.set("name",v.name());p.set("ready",v.ready());}return y.saveToString();});state=next;
    }
    static String value(UUID id){return id==null?"":id.toString();}static UUID uuid(ConfigurationSection p,String k)throws IOException{return UUID.fromString(s(p,k));}static UUID optional(ConfigurationSection p,String k)throws IOException{String v=s(p,k);return v.isEmpty()?null:UUID.fromString(v);}
    static ConfigurationSection section(ConfigurationSection p,String k)throws IOException{var s=p.getConfigurationSection(k);if(s==null)throw new IOException("Нет раздела "+k);return s;}
    static String s(ConfigurationSection p,String k)throws IOException{if(!p.isString(k))throw new IOException("Нет строки "+k);return p.getString(k);}
    static long n(ConfigurationSection p,String k)throws IOException{Object v=p.get(k);if(!(v instanceof Integer||v instanceof Long))throw new IOException("Нужно целое: "+k);return ((Number)v).longValue();}
}
