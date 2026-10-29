package ru.neverland.townycontrol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.*;
import ru.neverland.core.*;

/** Disposable derived SQLite index. TSV journals remain the only authoritative evidence.
 * No connection, SQL wait or source read is allowed while holding SuiteState. */
final class AuditIndex {
    private static final Semaphore READERS=new Semaphore(2);
    private static final Object WRITE=new Object();
    private static final int MAX_FILES=4096,MAX_LINE=2_097_152;
    private static final long MAX_SOURCE_BYTES=512L*1024*1024,MAX_TOTAL_BYTES=2L*1024*1024*1024,MAX_ROWS=5_000_000;
    record Stats(long rows,int journals,int replaced){}
    record Selection(String sql,List<Object> args){}
    private record Stamp(long size,String modified,String key){
        static Stamp read(Path file)throws IOException {var a=Files.readAttributes(file,BasicFileAttributes.class);if(!a.isRegularFile())throw new IOException("Необычный файл журнала: "+file);return new Stamp(a.size(),a.lastModifiedTime().toString(),String.valueOf(a.fileKey()));}
    }
    @FunctionalInterface interface Read<T>{T read(Connection db,Stats stats)throws Exception;}
    static <T>T read(Path root,Read<T> body)throws Exception{
        if(SuiteState.held())throw new IllegalStateException("Индекс аудита читается только вне общей блокировки");
        if(!READERS.tryAcquire())throw new IOException("Индекс занят двумя проверками. Повторите позже");
        try{synchronized(WRITE){
            Path base=root.toAbsolutePath().normalize(),dir=base.resolve("NeverLandTownyControl/audit-index");Files.createDirectories(dir);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("derived-v1.sqlite"))){
                try(var s=db.createStatement()){s.execute("PRAGMA busy_timeout=1000");s.execute("PRAGMA journal_mode=DELETE");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA temp_store=FILE");s.execute("PRAGMA cache_size=-4096");s.execute("PRAGMA max_page_count=1048576");}
                schema(db);Stats stats=refresh(base,db);
                try(var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM records")){r.next();if(r.getLong(1)!=stats.rows())throw new IOException("Индекс неполон; выполните /nltaudit reindex. Исходные журналы сохранены");}
                return body.read(db,stats);
            }
        }}catch(SQLException ex){throw new IOException("Индекс аудита недоступен; выполните /nltaudit reindex. Исходные журналы сохранены. "+ex.getMessage(),ex);}
        finally{READERS.release();}
    }
    static Stats rebuild(Path root)throws Exception{
        if(SuiteState.held())throw new IllegalStateException("Пересборка индекса выполняется вне общей блокировки");
        synchronized(WRITE){
            Path cache=root.toAbsolutePath().normalize().resolve("NeverLandTownyControl/audit-index/derived-v1.sqlite");
            // Only this disposable cache is removed; never a module's audit directory or ledger.
            Files.deleteIfExists(cache);Files.deleteIfExists(cache.resolveSibling(cache.getFileName()+"-journal"));
            return read(root,(db,stats)->stats);
        }
    }
    private static void schema(Connection db)throws SQLException{
        try(var s=db.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS sources(path TEXT PRIMARY KEY,size INTEGER NOT NULL,modified TEXT NOT NULL,file_key TEXT NOT NULL,rows INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS records(source TEXT NOT NULL,ordinal INTEGER NOT NULL,id TEXT NOT NULL,at INTEGER NOT NULL,module TEXT NOT NULL,kind TEXT NOT NULL,outcome TEXT NOT NULL,operation TEXT NOT NULL,actor_id TEXT NOT NULL,actor_type TEXT NOT NULL,actor_town TEXT NOT NULL,from_id TEXT NOT NULL,from_type TEXT NOT NULL,from_town TEXT NOT NULL,to_id TEXT NOT NULL,to_type TEXT NOT NULL,to_town TEXT NOT NULL,encoded TEXT NOT NULL,PRIMARY KEY(source,ordinal))");
            for(String column:List.of("id","at","module","kind","outcome","operation","actor_id","from_id","to_id","actor_town","from_town","to_town"))s.execute("CREATE INDEX IF NOT EXISTS idx_"+column+" ON records("+column+")");
            for(String column:List.of("id","module","kind","outcome","operation"))s.execute("CREATE INDEX IF NOT EXISTS idx_ci_"+column+" ON records("+column+" COLLATE NOCASE)");
        }
    }
    private static Stats refresh(Path root,Connection db)throws Exception{
        long deadline=System.nanoTime()+30_000_000_000L;var directories=AuditReader.directories(root);var files=new ArrayList<Path>();
        for(var dir:directories){files.addAll(AuditJournal.files(dir));if(files.size()>MAX_FILES)throw new IOException("Больше 4096 файлов аудита; требуется архивирование администратора");}
        long bytes=0;for(var file:files){bytes=Math.addExact(bytes,Files.size(file));if(bytes>MAX_TOTAL_BYTES)throw new IOException("Больше 2 ГиБ исходных журналов; вынесите старые месяцы в архив перед индексацией");}
        var live=new HashSet<String>();int replaced=0;db.setAutoCommit(false);
        try{
            for(Path file:files){checkTime(deadline);String key=root.relativize(file).toString();live.add(key);Stamp stamp=Stamp.read(file);boolean same=false;
                try(var q=db.prepareStatement("SELECT size,modified,file_key FROM sources WHERE path=?")){q.setString(1,key);try(var r=q.executeQuery()){same=r.next()&&r.getLong(1)==stamp.size()&&r.getString(2).equals(stamp.modified())&&r.getString(3).equals(stamp.key());}}
                if(same)continue;replace(db,key,file,stamp,deadline);replaced++;
            }
            var removed=new ArrayList<String>();try(var s=db.createStatement();var r=s.executeQuery("SELECT path FROM sources")){while(r.next())if(!live.contains(r.getString(1)))removed.add(r.getString(1));}
            for(String path:removed)erase(db,path);
            long count;try(var s=db.createStatement();var r=s.executeQuery("SELECT COALESCE(SUM(rows),0) FROM sources")){r.next();count=r.getLong(1);}
            if(count>MAX_ROWS)throw new IOException("Больше 5000000 записей аудита; требуется архивирование администратора");
            db.commit();return new Stats(count,directories.size(),replaced);
        }catch(Exception ex){try{db.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}throw ex;}
        finally{db.setAutoCommit(true);}
    }
    private static void erase(Connection db,String key)throws SQLException{for(String table:List.of("records","sources"))try(var s=db.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("records")?"source":"path")+"=?")){s.setString(1,key);s.executeUpdate();}}
    private static void replace(Connection db,String key,Path file,Stamp before,long deadline)throws Exception{
        if(before.size()>MAX_SOURCE_BYTES)throw new IOException("Файл аудита больше 512 МиБ: "+key);erase(db,key);long[] ordinal={0},batchChars={0};
        try(var insert=db.prepareStatement("INSERT INTO records VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            scanSnapshot(file,before.size(),(r,line)->{
                checkTime(deadline);long n=++ordinal[0];
                Object[] values={key,n,r.id(),r.at(),r.module(),r.kind(),r.outcome(),r.operation(),r.actor().id(),r.actor().type(),r.actor().town(),r.from().id(),r.from().type(),r.from().town(),r.to().id(),r.to().type(),r.to().town(),line};
                for(int i=0;i<values.length;i++)insert.setObject(i+1,values[i]);insert.addBatch();batchChars[0]+=line.length();
                if(n%128==0||batchChars[0]>=4_194_304){insert.executeBatch();insert.clearBatch();batchChars[0]=0;}
            });insert.executeBatch();insert.clearBatch();
        }
        Stamp after=Stamp.read(file);
        // The append-only writer may add complete records after our captured EOF. They belong
        // to the next snapshot. Replacement, truncation and same-size edits remain failures.
        if(!before.key().equals(after.key())||after.size()<before.size()||after.size()==before.size()&&!before.modified().equals(after.modified()))throw new IOException("Журнал заменён во время проверки; повторите запрос: "+key);
        try(var q=db.prepareStatement("INSERT INTO sources VALUES(?,?,?,?,?)")){q.setString(1,key);q.setLong(2,before.size());q.setString(3,before.modified());q.setString(4,before.key());q.setLong(5,ordinal[0]);q.executeUpdate();}
    }
    @FunctionalInterface interface RecordConsumer {void accept(AuditRecord record,String encoded)throws Exception;}
    static void scanSnapshot(Path file,long length,RecordConsumer consume)throws Exception{
        long deadline=System.nanoTime()+30_000_000_000L;long count=0;
        try(var reader=new BufferedReader(new InputStreamReader(new LimitedInput(Files.newInputStream(file),length),StandardCharsets.UTF_8))){
            String value;while((value=line(reader))!=null){checkTime(deadline);if(++count>MAX_ROWS)throw new IOException("Слишком много записей в "+file);consume.accept(AuditJournal.decode(value),value);}
        }
    }
    private static final class LimitedInput extends FilterInputStream {
        long remaining;LimitedInput(InputStream in,long size){super(in);remaining=size;}
        @Override public int read()throws IOException{if(remaining==0)return -1;int value=super.read();if(value<0)throw new EOFException("Журнал усечён во время чтения");remaining--;return value;}
        @Override public int read(byte[] b,int off,int len)throws IOException{if(remaining==0)return -1;int n=in.read(b,off,(int)Math.min(len,remaining));if(n<0)throw new EOFException("Журнал усечён во время чтения");remaining-=n;return n;}
    }
    private static String line(Reader in)throws IOException{var line=new StringBuilder();for(int ch;(ch=in.read())>=0;){if(ch=='\n')return line.toString();if(line.length()>=MAX_LINE)throw new IOException("Запись аудита больше 2 МиБ");line.append((char)ch);}if(line.length()!=0)throw new IOException("Незавершённая запись аудита");return null;}
    private static void checkTime(long deadline)throws IOException{if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("Проверка индекса превысила 30 секунд; результат не выдан как полный");}
    static Selection select(Predicate<AuditRecord> filter){
        var clauses=new ArrayList<String>();var values=new ArrayList<Object>();
        if(filter instanceof AuditQuery query)for(var e:query.filters().entrySet()){
            String v=e.getValue();if(Set.of("id","module","kind","outcome").contains(e.getKey())&&v.chars().anyMatch(c->c>127))continue;
            switch(e.getKey()){
                case "id"->{clauses.add("(id=? COLLATE NOCASE OR operation=? COLLATE NOCASE)");values.add(v);values.add(v);}
                case "module"->{clauses.add("(module=? COLLATE NOCASE OR module=? COLLATE NOCASE)");values.add(v);values.add("NeverLandTowny"+v);}
                case "kind","outcome"->{clauses.add(e.getKey()+"=? COLLATE NOCASE");values.add(v);}
                case "town"->{clauses.add("(actor_town=? OR from_town=? OR to_town=?)");values.add(v);values.add(v);values.add(v);}
                case "player"->{clauses.add("((actor_type='PLAYER' AND actor_id=?) OR (from_type='PLAYER' AND from_id=?) OR (to_type='PLAYER' AND to_id=?))");values.add(v);values.add(v);values.add(v);}
                case "from","to"->{clauses.add("("+e.getKey()+"_id=? OR "+e.getKey()+"_town=?)");values.add(v);values.add(v);}
                case "since","until"->{clauses.add("at"+(e.getKey().equals("since")?">=":"<")+"?");var date=LocalDate.parse(v);if(e.getKey().equals("until"))date=date.plusDays(1);values.add(date.atStartOfDay(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli());}
                default->{/* category/text have an exact Java predicate after indexed reduction. */}
            }
        }
        return new Selection(clauses.isEmpty()?"1=1":String.join(" AND ",clauses),List.copyOf(values));
    }
    static void scan(Connection db,Predicate<AuditRecord> filter,Consumer<AuditRecord> consumer)throws Exception{
        long deadline=System.nanoTime()+30_000_000_000L;var selection=select(filter);try(var query=db.prepareStatement("SELECT encoded FROM records WHERE "+selection.sql()+" ORDER BY at DESC,id DESC,module DESC,source,ordinal")){
            for(int i=0;i<selection.args().size();i++)query.setObject(i+1,selection.args().get(i));query.setQueryTimeout(30);
            try(var result=query.executeQuery()){while(result.next()){checkTime(deadline);var record=AuditJournal.decode(result.getString(1));if(filter.test(record))consumer.accept(record);}}
        }
    }
}
