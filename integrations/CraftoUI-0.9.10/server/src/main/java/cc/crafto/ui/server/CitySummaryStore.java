package cc.crafto.ui.server;
import cc.crafto.ui.Protocol;
import java.nio.file.*;
import java.util.*;
/** All methods are called on async workers. Disk checkpoints survive restarts. */
final class CitySummaryStore {
    private final Path root;
    CitySummaryStore(Path root){this.root=root;}
    synchronized CitySnapshot read(UUID id)throws Exception{
        Path file=root.resolve(id+".json");if(!Files.exists(file))return null;
        if(Files.size(file)>131072)throw new IllegalArgumentException("Summary too large");
        var json=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(Protocol.integer(json,"schema",0)!=1)throw new IllegalArgumentException("Unsupported summary schema");
        return Protocol.JSON.fromJson(json.get("snapshot"),CitySnapshot.class);
    }
    synchronized void write(UUID id,CitySnapshot value)throws Exception{
        Files.createDirectories(root);Path file=root.resolve(id+".json"),temp=root.resolve(id+".tmp");
        CitySnapshot existing;
        try{existing=read(id);}catch(Exception ignored){existing=null;}
        if(existing!=null&&existing.observedAt()>value.observedAt())return;
        String text=Protocol.JSON.toJson(Protocol.object("schema",1,"snapshot",value));
        if(text.length()>131072)throw new IllegalArgumentException("Summary too large");
        Files.writeString(temp,text);
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
