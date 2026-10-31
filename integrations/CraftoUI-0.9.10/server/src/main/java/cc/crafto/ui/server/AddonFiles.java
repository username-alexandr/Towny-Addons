package cc.crafto.ui.server;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
/** Shared bounded file cache; never called on player/region threads. Returned YAML is read-only. */
final class AddonFiles {
    private record Cached(long at,long modified,long size,YamlConfiguration yaml){}
    private final Map<Path,Cached> cache=new HashMap<>();
    synchronized YamlConfiguration read(Path path)throws Exception{
        if(!Files.exists(path))return new YamlConfiguration();
        long now=System.nanoTime();Cached old=cache.get(path);if(old!=null&&now-old.at()<8_000_000_000L)return old.yaml();
        long size=Files.size(path),modified=Files.getLastModifiedTime(path).toMillis();if(size>16*1024*1024)throw new IOException("Addon snapshot too large");
        if(old!=null&&old.modified()==modified&&old.size()==size){cache.put(path,new Cached(now,modified,size,old.yaml()));return old.yaml();}
        byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(16*1024*1024+1);}
        if(bytes.length>16*1024*1024)throw new IOException("Addon snapshot too large");
        YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        cache.put(path,new Cached(now,modified,size,yaml));return yaml;
    }
}
