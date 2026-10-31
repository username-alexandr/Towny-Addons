package cc.crafto.ui.client;
import cc.crafto.ui.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

final class ClientSettings {
    float scale=1;
    boolean blur=false,contractsHud=true,animations=true,darkTheme=false;
    float contractsScale=1;
    Map<String,PersonalPreferences> profiles=new LinkedHashMap<>();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("craftoui.json");
    static ClientSettings load(){
        try{if(Files.exists(FILE)&&Files.size(FILE)<1024*1024){ClientSettings s=Protocol.JSON.fromJson(Files.readString(FILE),ClientSettings.class);if(s!=null){
            s.contractsScale=Float.isFinite(s.contractsScale)?Math.clamp(s.contractsScale,.65f,1.3f):1;
            s.scale=Float.isFinite(s.scale)?Math.clamp(s.scale,.65f,1.3f):1;
            if(s.profiles==null)s.profiles=new LinkedHashMap<>();
            s.profiles.entrySet().removeIf(e->e.getKey().length()>256||e.getValue()==null);
            while(s.profiles.size()>32)s.profiles.remove(s.profiles.keySet().iterator().next());
            s.profiles.values().forEach(PersonalPreferences::sanitize);return s;
        }}}catch(Exception e){CraftoUIClient.LOG.warn("Cannot read CraftoUI settings; defaults used",e);}
        return new ClientSettings();
    }
    PersonalPreferences profile(String key){
        PersonalPreferences p=profiles.get(key);if(p!=null)return p;
        while(profiles.size()>=32)profiles.remove(profiles.keySet().iterator().next());
        p=new PersonalPreferences();p.scale=contractsScale;profiles.put(key,p);return p;
    }
    private static CompletableFuture<Void> saves=CompletableFuture.completedFuture(null);
    synchronized void save(){
        String json=Protocol.JSON.toJson(this);
        saves=saves.handle((v,e)->null).thenRunAsync(()->{
            try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling("craftoui.json.tmp");Files.writeString(temp,json);
                try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}
            }catch(Exception e){CraftoUIClient.LOG.warn("Cannot save CraftoUI settings",e);}
        });
    }
}
