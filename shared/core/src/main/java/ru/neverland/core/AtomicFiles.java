package ru.neverland.core;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Synchronous durable file replacement; the snapshot supplier must run in its owner's permitted thread. */
public final class AtomicFiles {
    @FunctionalInterface public interface Snapshot { String encode() throws IOException; }
    public record Metrics(String file,long writes,long failures,long bytes,long totalNanos,long maximumNanos) {}
    private static final Map<Path,Metrics> metrics=new HashMap<>();
    private static final Set<Path> blocked=new HashSet<>();
    private AtomicFiles() {}
    public static synchronized List<Metrics> metrics() { return List.copyOf(metrics.values()); }
    /** Called only after a repository has successfully validated its file on load. */
    public static synchronized void loaded(Path file) { blocked.remove(file.toAbsolutePath().normalize()); }
    public static synchronized boolean writable(Path file) { return !blocked.contains(file.toAbsolutePath().normalize()); }
    public static void write(Path file,Snapshot snapshot)throws IOException {
        Path target=file.toAbsolutePath().normalize();if(!writable(target))throw new IOException("Запись остановлена после ошибки: "+target.getFileName());
        long start=System.nanoTime(),size=0;boolean success=false;Path temp=null;
        try {
            byte[] bytes=snapshot.encode().getBytes(StandardCharsets.UTF_8);size=bytes.length;Files.createDirectories(target.getParent());temp=Files.createTempFile(target.getParent(),"neverland-",".tmp");
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
            success=true;
        } catch(IOException | RuntimeException ex) {synchronized(AtomicFiles.class){blocked.add(target);}throw new IOException("Не удалось сохранить "+target.getFileName()+"; новые записи остановлены",ex);}
        finally {
            long elapsed=System.nanoTime()-start;
            synchronized(AtomicFiles.class){Metrics old=metrics.getOrDefault(target,new Metrics(target.toString(),0,0,0,0,0));metrics.put(target,new Metrics(target.toString(),old.writes()+(success?1:0),old.failures()+(success?0:1),old.bytes()+(success?size:0),old.totalNanos()+elapsed,Math.max(old.maximumNanos(),elapsed)));}
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){/* Keep the original write outcome; an orphan temp does not invalidate committed data. */}
        }
    }
}
