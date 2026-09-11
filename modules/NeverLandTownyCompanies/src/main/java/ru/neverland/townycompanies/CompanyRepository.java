package ru.neverland.townycompanies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import static ru.neverland.townycompanies.CompanyData.*;

public final class CompanyRepository implements CompanyLedger.Store {
    private final Path file;
    private CompanyLedger.State state=CompanyLedger.State.empty();
    private boolean writable;
    public CompanyRepository(Path file){this.file=file;}
    public CompanyLedger.State state(){return state;}
    public boolean writable(){return writable;}
    public void load()throws IOException {
        writable=false;
        try {
            var cs=new LinkedHashMap<UUID,Company>();var ps=new LinkedHashMap<UUID,Payment>();var rs=new LinkedHashMap<UUID,Receipt>();
            if(Files.exists(file)) {
                var y=new YamlConfiguration();y.load(file.toFile());if(num(y,"schema")!=1)throw new IOException("Неизвестная версия companies.yml");
                var root=section(y,"companies");
                for(String key:root.getKeys(false)) {
                    var s=section(root,key);var members=new LinkedHashMap<UUID,Role>();var invites=new LinkedHashMap<UUID,Long>();
                    var m=section(s,"members");for(String id:m.getKeys(false))members.put(UUID.fromString(id),Role.valueOf(text(m,id)));
                    var i=section(s,"invitations");for(String id:i.getKeys(false))invites.put(UUID.fromString(id),num(i,id));
                    UUID id=UUID.fromString(key);String successor=text(s,"successor");
                    cs.put(id,new Company(id,uuid(s,"town"),text(s,"name"),Kind.valueOf(text(s,"kind")),uuid(s,"owner"),members,invites,
                            successor.isEmpty()?null:UUID.fromString(successor),num(s,"successor-until"),num(s,"balance"),num(s,"debt"),num(s,"next-tax"),num(s,"revision"),bool(s,"closed")));
                }
                root=section(y,"payments");for(String key:root.getKeys(false)){var s=section(root,key);UUID id=UUID.fromString(key);ps.put(id,new Payment(id,uuid(s,"company"),uuid(s,"account"),Purpose.valueOf(text(s,"purpose")),num(s,"amount"),Phase.valueOf(text(s,"phase")),num(s,"created")));}
                root=section(y,"receipts");for(String key:root.getKeys(false)){var s=section(root,key);UUID id=UUID.fromString(key);rs.put(id,new Receipt(id,uuid(s,"company"),uuid(s,"town"),num(s,"payout"),num(s,"refund")));}
            }
            state=new CompanyLedger.State(cs,ps,rs);writable=true;
        }catch(Exception ex){throw new IOException("companies.yml повреждён; операции остановлены",ex);}
    }
    public void save(CompanyLedger.State next)throws IOException {
        if(!writable)throw new IOException("Запись компаний остановлена. Проверьте companies.yml и перезапустите сервер");
        try {
            var y=new YamlConfiguration();y.set("schema",1);y.createSection("companies");y.createSection("payments");y.createSection("receipts");
            for(var c:next.companies().values()) {
                var s=y.createSection("companies."+c.id());s.set("town",c.town().toString());s.set("name",c.name());s.set("kind",c.kind().name());s.set("owner",c.owner().toString());
                s.createSection("members");c.members().forEach((id,role)->s.set("members."+id,role.name()));s.createSection("invitations");c.invitations().forEach((id,time)->s.set("invitations."+id,time));
                s.set("successor",c.successor()==null?"":c.successor().toString());s.set("successor-until",c.successorUntil());s.set("balance",c.balance());s.set("debt",c.debt());s.set("next-tax",c.nextTax());s.set("revision",c.revision());s.set("closed",c.closed());
            }
            for(var p:next.payments().values()){var s=y.createSection("payments."+p.id());s.set("company",p.company().toString());s.set("account",p.account().toString());s.set("purpose",p.purpose().name());s.set("amount",p.amount());s.set("phase",p.phase().name());s.set("created",p.created());}
            for(var r:next.receipts().values()){var s=y.createSection("receipts."+r.contract());s.set("company",r.company().toString());s.set("town",r.town().toString());s.set("payout",r.payout());s.set("refund",r.refund());}
            atomic(y,file);state=next;
        }catch(IOException|RuntimeException ex){writable=false;throw new IOException("Не удалось сохранить компании; денежные операции остановлены",ex);}
    }
    public static void atomic(YamlConfiguration y,Path path)throws IOException {
        Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"companies-",".tmp");
        try{y.save(tmp.toFile());try(var ch=FileChannel.open(tmp,StandardOpenOption.WRITE)){ch.force(true);}try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
    private static ConfigurationSection section(ConfigurationSection s,String k)throws IOException{var v=s.getConfigurationSection(k);if(v==null)throw new IOException("Нет раздела "+k);return v;}
    private static String text(ConfigurationSection s,String k)throws IOException{if(!s.isString(k))throw new IOException("Нет строки "+k);return s.getString(k);}
    private static UUID uuid(ConfigurationSection s,String k)throws IOException{return UUID.fromString(text(s,k));}
    private static long num(ConfigurationSection s,String k)throws IOException{Object v=s.get(k);if(!(v instanceof Number n)||n.doubleValue()!=n.longValue())throw new IOException("Нет целого "+k);return n.longValue();}
    private static boolean bool(ConfigurationSection s,String k)throws IOException{if(!s.isBoolean(k))throw new IOException("Нет флага "+k);return s.getBoolean(k);}
}
