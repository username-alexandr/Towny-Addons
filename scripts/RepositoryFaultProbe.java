import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Only a directory and logger are needed to exercise real repository I/O. */
public final class RepositoryFaultProbe {
    public static class Fixture extends JavaPlugin {
        public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("repository-fault"); }
    }
    static void fail(Runnable call, String message) {
        try { call.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }
    static Object invoke(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("repository-fault-");
        var uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        var plugin = (Fixture)((sun.misc.Unsafe)uf.get(null)).allocateInstance(Fixture.class);
        var df = JavaPlugin.class.getDeclaredField("dataFolder"); df.setAccessible(true); df.set(plugin, dir.toFile());
        var type = Class.forName(args[0]); Object repo = type.getConstructor(JavaPlugin.class).newInstance(plugin);
        invoke(repo, "load"); var dirty = type.getDeclaredField("dirty"); dirty.setAccessible(true);
        dirty.setBoolean(repo, true); invoke(repo, "save");
        Path file = dir.resolve(args[1]), backup = dir.resolve("backup.yml"); byte[] original = Files.readAllBytes(file);
        Files.move(file, backup); Files.createDirectory(file); Files.writeString(file.resolve("keep"), "original");
        dirty.setBoolean(repo, true); fail(() -> invoke(repo, "save"), "failed write must reach caller");
        Files.delete(file.resolve("keep")); Files.delete(file); Files.move(backup, file);
        fail(() -> invoke(repo, "save"), "restoring target must not reopen the gate");
        if (!Arrays.equals(original, Files.readAllBytes(file))) throw new AssertionError("last committed version changed");
        invoke(repo, "load"); Files.writeString(file, args[2] + ": wrong-section\n"); byte[] corrupt = Files.readAllBytes(file);
        fail(() -> invoke(repo, "load"), "corruption must stop load"); dirty.setBoolean(repo, true);
        fail(() -> invoke(repo, "save"), "corrupt storage must remain blocked");
        if (!Arrays.equals(corrupt, Files.readAllBytes(file))) throw new AssertionError("corrupt database overwritten");
        System.out.println("REPOSITORY FAULT PASS: " + type.getSimpleName());
    }
}
