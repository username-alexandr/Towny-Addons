package ru.neverland.townytreasury;
import java.nio.file.*;import java.lang.reflect.Proxy;import java.util.*;
import org.bukkit.plugin.Plugin;import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townytreasury.config.TreasurySettings;import ru.neverland.townytreasury.service.TreasuryService;import ru.neverland.townytreasury.data.TreasuryRepository;
public final class CategoryResolutionSmoke{
 public static void main(String[] args)throws Exception{
  var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/config.yml").toFile());var normal=TreasurySettings.load(yaml);var plugin=(Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(p,m,a)->m.getName().equals("getName")?"CategoryResolutionSmoke":m.getName().equals("getLogger")?java.util.logging.Logger.getAnonymousLogger():m.getReturnType()==boolean.class?true:null);
  var repo=new TreasuryRepository(Files.createTempDirectory("category-resolution").resolve("treasury.yml"));repo.load();var service=new TreasuryService(plugin,repo,normal);
  String frozen=service.resolveCategory("project/army");if(!frozen.equals("army")||!service.resolveCategory("project/infirmary").equals("social")||!service.resolveCategory("social").equals("social"))throw new AssertionError("legacy default project mappings resolve through actual additive API");service.stop().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
  yaml.set("upkeep-categories.army","construction");var changed=TreasurySettings.load(yaml);var other=new TreasuryRepository(Files.createTempDirectory("category-custom").resolve("treasury.yml"));other.load();var configured=new TreasuryService(plugin,other,changed);if(!configured.resolveCategory("project/army").equals("construction")||!frozen.equals("army"))throw new AssertionError("custom project mapping resolves for new intents without modifying the caller's frozen category");configured.stop().toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
  System.out.println("CategoryResolutionSmoke PASS 2 checks");
 }
}
