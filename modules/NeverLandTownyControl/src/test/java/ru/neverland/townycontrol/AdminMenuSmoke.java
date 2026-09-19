package ru.neverland.townycontrol;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;

public final class AdminMenuSmoke {
    public static void main(String[] args)throws Exception {
        var meta=new org.bukkit.plugin.PluginDescriptionFile(Objects.requireNonNull(AdminMenuSmoke.class.getResourceAsStream("/plugin.yml")));
        var catalog=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(Objects.requireNonNull(AdminMenuSmoke.class.getResourceAsStream("/admin-modules.yml")),java.nio.charset.StandardCharsets.UTF_8));
        var permission=meta.getPermissions().stream().filter(p->p.getName().equals(AdminAccess.ADMIN)).findFirst().orElseThrow();
        assert permission.getDefault()==org.bukkit.permissions.PermissionDefault.OP;
        var children=permission.getChildren();
        for(String module:catalog.getConfigurationSection("modules").getKeys(false))assert Boolean.TRUE.equals(children.get(catalog.getString("modules."+module+".permission"))):module;
        assert Boolean.TRUE.equals(children.get(AdminAccess.AUDIT))&&Boolean.TRUE.equals(children.get(AdminAccess.EXPORT));
        assert catalog.getBoolean("modules.Events.activities");
        Path root=Files.createTempDirectory("admin-menu-audit-");var from=new AuditRecord.Party("TOWN","one","Первый","one");var to=new AuditRecord.Party("TOWN","two","Второй","two");
        var expected=new ArrayList<AuditRecord>();long time=1_800_000_000_000L;
        for(int i=0;i<90;i++){
            String module=i%2==0?"NeverLandTownyTrade":"NeverLandTownyMarket",kind=i%2==0?"CARAVAN_DEAL":"MARKET_DEAL";
            var record=new AuditRecord(UUID.nameUUIDFromBytes(("row-"+i).getBytes()).toString(),time+i,module,kind,"COMPLETE","op-"+i,AuditRecord.Party.system(),from,to,"DIAMOND",i,"1.00","");
            new AuditJournal(root.resolve(module).resolve("audit")).append(record);expected.add(record);
        }
        var filter=AuditQuery.parse(new String[]{"category=markets","intercity=true","outcome=COMPLETE"});
        var first=AuditReader.page(root,filter,1,36);var second=AuditReader.page(root,filter,2,36);
        assert first.matched()==45&&first.scanned()==90&&first.journals()==2&&first.rows().size()==36&&second.rows().size()==9;
        assert first.rows().get(0).operation().equals("op-89")&&second.rows().get(0).operation().equals("op-17");
        assert Collections.disjoint(first.rows(),second.rows());
        assert AuditReader.page(root,AuditQuery.parse(new String[]{"category=taxes"}),1,36).rows().isEmpty();
        for(var category:AuditCategory.values())if(category!=AuditCategory.ALL&&category!=AuditCategory.OTHER)for(String kind:category.kinds){
            var record=new AuditRecord(UUID.randomUUID().toString(),time,"NeverLandTownyControl",kind,"COMPLETE","op",AuditRecord.Party.system(),from,to,"",0,"","");
            assert category.test(record)&&!AuditCategory.OTHER.test(record);
            assert Arrays.stream(AuditCategory.values()).filter(c->c!=AuditCategory.ALL).filter(c->c.test(record)).count()==1:kind;
            assert !AuditCategory.kind(kind).equals(kind):kind;
        }
        var unknown=new AuditRecord(UUID.randomUUID().toString(),time,"NeverLandTownyFuture","FUTURE","UNKNOWN","op",AuditRecord.Party.system(),from,to,"",0,"","");assert AuditCategory.OTHER.test(unknown)&&AuditCategory.ALL.test(unknown);
        boolean rejected=false;try{AuditQuery.parse(new String[]{"category=missing"});}catch(IllegalArgumentException expectedFailure){rejected=true;}assert rejected;
        Path file=AuditJournal.files(root.resolve("NeverLandTownyTrade/audit")).get(0);Files.writeString(file,"torn",StandardOpenOption.APPEND);
        rejected=false;try{AuditReader.page(root,filter,1,36);}catch(java.io.IOException expectedFailure){rejected=true;}assert rejected:"Even corruption outside selected category must fail the full query";
        System.out.println("AdminMenuSmoke PASS: permission inheritance, Events adapter, exclusive categories, bounded pagination, filters, corrupt source refusal");
    }
}
