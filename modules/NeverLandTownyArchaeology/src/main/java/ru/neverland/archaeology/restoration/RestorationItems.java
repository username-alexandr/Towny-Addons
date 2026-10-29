package ru.neverland.archaeology.restoration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.util.*;
import ru.neverland.core.AtomicFiles;
import static ru.neverland.archaeology.restoration.RestorationBook.*;
/** Separate key and PDC namespace: old artifacts/batches keep their exact previous identity. */
public final class RestorationItems {
    private final NamespacedKey id,signature;private final String secret;private final ArchaeologyRegistry registry;
    public RestorationItems(JavaPlugin plugin,ArchaeologyRegistry registry)throws java.io.IOException{this.registry=registry;id=new NamespacedKey(plugin,"fragment_serial");signature=new NamespacedKey(plugin,"fragment_signature");secret=secret(plugin.getDataFolder().toPath());}
    static String secret(Path directory)throws java.io.IOException{Path file=directory.resolve("restoration.key");if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)throw new java.io.IOException("Повреждён ключ реставрации");String value=Files.readString(file,StandardCharsets.UTF_8).trim();if(!value.matches("[A-Za-z0-9_-]{43}"))throw new java.io.IOException("Повреждён ключ реставрации");return value;}if(Files.exists(directory.resolve("restoration.yml")))throw new java.io.IOException("Ключ реставрации утрачен; новый ключ не создаётся поверх прежних фрагментов");String generated=CryptoUtil.secret();AtomicFiles.writeDetached(file,generated+"\n");return generated;}
    static String payload(Fragment fragment){return "fragment-v1|"+fragment.id()+"|"+fragment.source()+"|"+fragment.recipe().artifact()+"|"+fragment.recipe().rarity()+"|"+fragment.recipe().parts()+"|"+fragment.part()+"|"+fragment.at();}
    public double draw(UUID source){byte[] bytes=Base64.getUrlDecoder().decode(CryptoUtil.sign(secret,"fragment-draw-v1|"+source));long value=java.nio.ByteBuffer.wrap(bytes).getLong()>>>11;return value*0x1.0p-53;}
    public int part(UUID source,int count){byte[] bytes=Base64.getUrlDecoder().decode(CryptoUtil.sign(secret,"fragment-part-v1|"+source));return 1+Math.floorMod(java.nio.ByteBuffer.wrap(bytes).getInt(),count);}
    public ItemStack create(Fragment fragment){var item=new ItemStack(Material.BRICK);var meta=item.getItemMeta();var definition=registry.artifact(fragment.recipe().artifact());String name=definition==null?fragment.recipe().artifact():definition.name();meta.setDisplayName(ColorUtil.color("&eФрагмент: "+name));meta.setLore(List.of(ColorUtil.color("&7Часть: &f"+fragment.part()+" / "+fragment.recipe().parts()),ColorUtil.color("&7Соберите все разные части этого артефакта."),ColorUtil.color("&7Реставрация: &f/archaeology restore"),ColorUtil.color("&8UUID: "+fragment.id())));meta.setMaxStackSize(1);meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);meta.getPersistentDataContainer().set(id,PersistentDataType.STRING,fragment.id().toString());meta.getPersistentDataContainer().set(signature,PersistentDataType.STRING,CryptoUtil.sign(secret,payload(fragment)));item.setItemMeta(meta);return item;}
    public Fragment identify(ItemStack item,State book){if(item==null||item.getType()!=Material.BRICK||item.getAmount()!=1||!item.hasItemMeta())return null;ItemMeta meta=item.getItemMeta();String raw=meta.getPersistentDataContainer().get(id,PersistentDataType.STRING),signed=meta.getPersistentDataContainer().get(signature,PersistentDataType.STRING);if(raw==null||signed==null)return null;try{var fragment=book.fragments().get(UUID.fromString(raw));if(fragment==null||!CryptoUtil.equals(signed,CryptoUtil.sign(secret,payload(fragment))))return null;return fragment;}catch(IllegalArgumentException invalid){return null;}}
}
