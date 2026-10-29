package ru.neverland.runtime;
import java.nio.file.*;import java.sql.*;import java.lang.reflect.*;import java.util.*;
/** Opt-in disposable fixture instrumentation, never packaged in an addon. Delegates exact JDBC behavior. */
final class SqliteTrace {
    static Path output;static String database;static long connections;
    static synchronized void log(String text){try{Files.writeString(output,System.currentTimeMillis()+" thread="+Thread.currentThread().getName()+" "+text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}
    static Object call(Object object,Method method,Object[] args)throws Throwable{try{return method.invoke(object,args);}catch(InvocationTargetException e){throw e.getCause();}}
    static void install(Path file,Path expected)throws Exception{
        output=file;database=expected.toAbsolutePath().normalize().toString();Class.forName("org.sqlite.JDBC");Driver original=DriverManager.getDriver("jdbc:sqlite:"+database);log("driver="+original.getClass()+" loader="+original.getClass().getClassLoader());
        Driver wrapper=(Driver)Proxy.newProxyInstance(Driver.class.getClassLoader(),new Class<?>[]{Driver.class},(proxy,m,args)->{
            Object value=call(original,m,args);if(m.getName().equals("connect")&&value instanceof Connection c&&String.valueOf(args[0]).contains("server-materials.db")){long id; synchronized(SqliteTrace.class){id=++connections;}log("CONNECT "+id+" url="+args[0]+" class="+c.getClass()+" loader="+c.getClass().getClassLoader());return connection(c,id);}return value;
        });DriverManager.deregisterDriver(original);DriverManager.registerDriver(wrapper);log("installed caller-visible delegating driver "+wrapper.getClass().getClassLoader());
        Path directory=expected.getParent();Files.createDirectories(directory);var watcher=FileSystems.getDefault().newWatchService();directory.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY);var thread=new Thread(()->{for(;;)try{var key=watcher.take();for(var event:key.pollEvents())if(String.valueOf(event.context()).startsWith("server-materials.db")){Path path=directory.resolve((Path)event.context());String state;try{state=Files.exists(path)?Files.readAttributes(path,"unix:ino,size,lastModifiedTime").toString():"absent";}catch(Exception ex){state=ex.toString();}log("FILE "+event.kind().name()+" "+event.context()+" "+state);}key.reset();}catch(Exception end){return;}},"native-sqlite-file-observer");thread.setDaemon(true);thread.start();
    }
    static Connection connection(Connection original,long id){return(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,m,args)->{
        if(Set.of("close","commit","rollback","setAutoCommit").contains(m.getName()))log("CONNECTION "+id+" BEFORE "+m.getName()+" "+Arrays.toString(args));Object value=call(original,m,args);
        if(Set.of("close","commit","rollback","setAutoCommit").contains(m.getName()))log("CONNECTION "+id+" AFTER "+m.getName());
        if(value instanceof Statement statement&&Set.of("createStatement","prepareStatement").contains(m.getName()))return statement(statement,id,args==null||args.length==0?"":String.valueOf(args[0]));return value;
    });}
    static Object statement(Statement original,long id,String prepared){Class<?> type=original instanceof PreparedStatement?PreparedStatement.class:Statement.class;return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,m,args)->{if(m.getName().startsWith("execute"))log("SQL "+id+" "+(prepared.isEmpty()?Arrays.toString(args):prepared));return call(original,m,args);});}
}
