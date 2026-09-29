package ru.neverland.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Append-only monthly TSV with escaped cells, SHA-256 checksums and durable ID deduplication. */
public final class AuditJournal {
    private final Path directory;
    private final Map<String,String> ids=new HashMap<>();
    private boolean loaded;
    private Path uniqueFile;
    public AuditJournal(Path directory){this.directory=directory;}
    public Path directory(){return directory;}
    public synchronized int size(){return ids.size();}
    public synchronized void load()throws IOException {
        loaded=false;ids.clear();scan(directory,r->{String old=ids.putIfAbsent(r.id(),fingerprint(r));if(old!=null&&!old.equals(fingerprint(r)))throw new IllegalArgumentException("Конфликт ID аудита "+r.id());});loaded=true;
    }
    public synchronized boolean append(AuditRecord r)throws IOException {
        if(!loaded)load();String fingerprint=fingerprint(r),old=ids.get(r.id());
        if(old!=null){if(!old.equals(fingerprint))throw new IOException("ID аудита занят другими условиями: "+r.id());return false;}
        write(r);
        ids.put(r.id(),fingerprint);return true;
    }
    /** Caller-generated random event IDs: no unbounded in-memory index for native bank traffic. */
    public synchronized void appendUnique(AuditRecord r)throws IOException {
        Path file=file(r);if(!file.equals(uniqueFile)){if(Files.exists(file))scanFile(file,x->{});uniqueFile=file;}
        write(r);
    }
    private Path file(AuditRecord r){return directory.resolve(Instant.ofEpochMilli(r.at()).atZone(ZoneOffset.UTC).toLocalDate().toString().substring(0,7)+".tsv");}
    private void write(AuditRecord r)throws IOException {
        Files.createDirectories(directory);Path file=file(r);
        byte[] bytes=(encode(r)+"\n").getBytes(StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){
            var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }catch(IOException ex){loaded=false;uniqueFile=null;throw ex;}
    }
    private static String fingerprint(AuditRecord r){return hash(String.join("\t",r.identity().stream().map(AuditJournal::escape).toList()));}
    public static String encode(AuditRecord r){String body=String.join("\t",r.fields().stream().map(AuditJournal::escape).toList());return body+"\t"+hash(body);}
    public static AuditRecord decode(String line)throws IOException {
        try{int i=line.lastIndexOf('\t');if(i<0||!hash(line.substring(0,i)).equals(line.substring(i+1)))throw new IOException("Не совпадает контрольная сумма аудита");return AuditRecord.parse(Arrays.stream(line.substring(0,i).split("\t",-1)).map(AuditJournal::unescape).toList());}
        catch(RuntimeException ex){throw new IOException("Повреждена запись аудита",ex);}
    }
    public static List<Path> files(Path directory)throws IOException {
        if(!Files.exists(directory))return List.of();try(var files=Files.list(directory)){return files.filter(p->p.getFileName().toString().matches("[0-9]{4}-[0-9]{2}\\.tsv")).sorted().toList();}
    }
    public static void scan(Path directory,Consumer<AuditRecord> consumer)throws IOException {
        for(Path file:files(directory))scanFile(file,consumer);
    }
    private static void scanFile(Path file,Consumer<AuditRecord> consumer)throws IOException {
            // Snapshot the committed file length. A concurrent append is excluded from this query.
            long length=Files.size(file);if(length==0)return;
            try(var channel=FileChannel.open(file,StandardOpenOption.READ)){
                var last=ByteBuffer.allocate(1);channel.read(last,length-1);if(last.array()[0]!='\n')throw new IOException("Незавершённая запись: "+file);
            }
            try(var input=Files.newInputStream(file);var reader=new BufferedReader(new InputStreamReader(new LimitedInput(input,length),StandardCharsets.UTF_8))){String line;long n=0;while((line=reader.readLine())!=null){n++;try{consumer.accept(decode(line));}catch(Exception ex){throw new IOException(file+":"+n+": "+ex.getMessage(),ex);}}}
    }
    private static final class LimitedInput extends FilterInputStream {
        private long remaining;LimitedInput(InputStream in,long size){super(in);remaining=size;}
        @Override public int read()throws IOException{if(remaining==0)return -1;int n=in.read();if(n>=0)remaining--;return n;}
        @Override public int read(byte[] b,int off,int len)throws IOException{if(remaining==0)return -1;int n=in.read(b,off,(int)Math.min(len,remaining));if(n>0)remaining-=n;return n;}
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r");}
    private static String unescape(String s){var b=new StringBuilder();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\\'){if(++i==s.length())throw new IllegalArgumentException("Оборванная строка");c=s.charAt(i);c=switch(c){case 't'->'\t';case 'n'->'\n';case 'r'->'\r';case '\\'->'\\';default->throw new IllegalArgumentException("Неверный escape");};}b.append(c);}return b.toString();}
    public static String csv(String s){String value=s.replace("\r"," ").replace("\n"," ");if(!value.isEmpty()&&"=+-@\t".indexOf(value.stripLeading().isEmpty()?' ':value.stripLeading().charAt(0))>=0)value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
}
