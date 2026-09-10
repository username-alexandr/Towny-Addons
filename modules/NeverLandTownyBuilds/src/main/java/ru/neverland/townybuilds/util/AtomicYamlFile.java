package ru.neverland.townybuilds.util;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.IOException;
public final class AtomicYamlFile {
    private AtomicYamlFile(){}
    public static void write(YamlConfiguration yaml,Path file)throws IOException{
        Path target=file.toAbsolutePath();Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"town-data-",".tmp");
        try{yaml.save(temp.toFile());try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
