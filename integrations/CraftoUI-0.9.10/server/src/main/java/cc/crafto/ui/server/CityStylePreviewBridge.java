package cc.crafto.ui.server;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import static cc.crafto.ui.Protocol.*;

/** A small sample city rendered from the Builds addon’s actual blueprint API. Optional integration. */
final class CityStylePreviewBridge {
    static final String HOLDER="ru.neverland.townybuilds.construction.CityStyles$View";
    private static final List<String> PROJECTS=List.of("town_hall","forge","market");
    private CityStylePreviewBridge(){}
    static String selected(Inventory inventory){
        if(inventory.getSize()!=27||inventory.getItem(13)==null)return "";
        return switch(inventory.getItem(13).getType()){
            case STONE_BRICKS->"MEDIEVAL";case QUARTZ_BLOCK->"MODERN";case AMETHYST_BLOCK->"FANTASY";default->"";
        };
    }
    static void load(CraftoUIPlugin plugin,Player player,Inventory inventory,String key,String selected){
        if(key.isEmpty()||selected.isBlank())return;
        Plugin builds=plugin.getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");
        if(builds==null||!builds.isEnabled())return;
        try{
            ClassLoader loader=builds.getClass().getClassLoader();
            Class<?> styleType=Class.forName("ru.neverland.townybuilds.construction.BuildingStyle",false,loader);
            Class<?> generatorType=Class.forName("ru.neverland.townybuilds.construction.BuildingBlueprintGenerator",false,loader);
            Class<?> siteType=Class.forName("ru.neverland.townybuilds.construction.ConstructionSite",false,loader);
            Class<?> catalog=Class.forName("ru.neverland.townybuilds.construction.BlueprintCatalog",false,loader);
            Object style=Enum.valueOf((Class)styleType,selected),generator=generatorType.getConstructor().newInstance();
            Method preview=catalog.getMethod("preview",String.class,int.class,siteType,styleType,String.class,generatorType);
            List<List<SampleBlock>> parts=new ArrayList<>();
            for(int i=0;i<PROJECTS.size();i++){
                Object plan=preview.invoke(null,PROJECTS.get(i),1,null,style,null,generator);
                @SuppressWarnings("unchecked") Map<Object,Object> site=(Map<Object,Object>)plan.getClass().getMethod("blocks").invoke(plan);
                List<SampleBlock> part=new ArrayList<>();
                for(var e:site.entrySet()){
                    Object at=e.getKey(),block=e.getValue();Class<?> offset=at.getClass();
                    Material material=(Material)block.getClass().getMethod("material").invoke(block);
                    if(material.isAir())continue;
                    part.add(new SampleBlock(((Number)offset.getMethod("x").invoke(at)).intValue(),((Number)offset.getMethod("y").invoke(at)).intValue(),((Number)offset.getMethod("z").invoke(at)).intValue(),material));
                    if(part.size()>30000)throw new IllegalArgumentException("Preview too large");
                }
                parts.add(List.copyOf(part));
            }
            List<List<SampleBlock>> committed=List.copyOf(parts);
            plugin.getServer().getAsyncScheduler().runNow(plugin,task->{
                try{
                    List<SampleBlock> snapshot=new ArrayList<>();
                    for(int i=0;i<committed.size();i++){
                        List<SampleBlock> current=staged(builds,loader,selected.toLowerCase(Locale.ROOT),PROJECTS.get(i));
                        if(current==null)current=committed.get(i);
                        int dx=i==0?0:i==1?-26:26,dz=i==0?-18:22;
                        for(var b:current)snapshot.add(new SampleBlock(b.x()+dx,b.y(),b.z()+dz,b.material()));
                    }
                    Class<?> voxel=Class.forName("ru.neverland.townybuilds.construction.BlueprintThumbnail$Voxel",false,loader);
                    Class<?> thumbnail=Class.forName("ru.neverland.townybuilds.construction.BlueprintThumbnail",false,loader);
                    Constructor<?> constructor=voxel.getConstructor(int.class,int.class,int.class,int.class,double.class);
                    Method render=thumbnail.getMethod("render",List.class,int.class,int.class);
                    List<Object> voxels=new ArrayList<>(snapshot.size());
                    Map<Material,Integer> colors=new HashMap<>();
                    for(var b:snapshot){int color=colors.computeIfAbsent(b.material(),m->{int value=m.createBlockData().getMapColor().asRGB();return value==0?0x98aab5:value;});
                        String name=b.material().name();double height=name.endsWith("_SLAB")?.5:name.endsWith("_CARPET")?.0625:1;
                        voxels.add(constructor.newInstance(b.x(),b.y(),b.z(),color,height));}
                    for(int view=0;view<4;view++){
                        byte[] png=(byte[])render.invoke(null,voxels,view,192);
                        if(png.length>16000)png=(byte[])render.invoke(null,voxels,view,128);
                        if(png.length>16000)continue;
                        String encoded=Base64.getEncoder().encodeToString(png);
                        int angle=view;player.getScheduler().run(plugin,t->{if(!valid(plugin,player,inventory,key))return;
                            var chunks=new ArrayList<String>();for(int j=0;j<encoded.length();j+=4000)chunks.add(encoded.substring(j,Math.min(j+4000,encoded.length())));
                            plugin.buildingPreview(player,object("key",key,"view",angle,"chunks",chunks));},null);
                    }
                }catch(Exception error){unavailable(plugin,player,inventory,key);}
            });
        }catch(Exception error){unavailable(plugin,player,inventory,key);}
    }
    /** Auto-detect completed builder exports for the preview; never mutates Builds' pinned live plans. */
    private static List<SampleBlock> staged(Plugin builds,ClassLoader loader,String style,String project){
        try{
            Path root=builds.getDataFolder().toPath().resolve("blueprints/source").toRealPath();
            Path folder=root.resolve(style).resolve(project);long now=System.currentTimeMillis();
            for(int level=1;level<=5;level++){
                Path file=folder.resolve(level+".schem");
                if(!Files.isRegularFile(file)||!file.toRealPath().startsWith(root)||Files.size(file)<32||Files.size(file)>16*1024*1024
                    ||now-Files.getLastModifiedTime(file).toMillis()<2000)return null;
            }
            Class<?> reader=Class.forName("ru.neverland.townybuilds.construction.SchematicReader",false,loader);
            Object model=reader.getMethod("read",Path.class).invoke(null,folder.resolve("1.schem"));
            Class<?> type=model.getClass();int width=((Number)type.getMethod("width").invoke(model)).intValue(),length=((Number)type.getMethod("length").invoke(model)).intValue();
            @SuppressWarnings("unchecked") Map<Integer,String> palette=(Map<Integer,String>)type.getMethod("palette").invoke(model);
            @SuppressWarnings("unchecked") List<Integer> cells=(List<Integer>)type.getMethod("blocks").invoke(model);
            Map<Integer,Material> materials=new HashMap<>();for(var e:palette.entrySet()){
                String block=e.getValue().replaceFirst("\\[.*$","");
                Material material=Material.matchMaterial(block);
                if(material==null)return null;materials.put(e.getKey(),material);
            }
            List<SampleBlock> result=new ArrayList<>();
            for(int n=0;n<cells.size();n++){
                Material material=materials.get(cells.get(n));if(material==null)return null;if(material.isAir())continue;
                int x=n%width,z=(n/width)%length,y=n/(width*length);
                result.add(new SampleBlock(x-width/2,y,z-length/2,material));
                if(result.size()>30000)return null;
            }
            return result.isEmpty()?null:List.copyOf(result);
        }catch(Exception ignored){return null;}
    }
    private static boolean valid(CraftoUIPlugin plugin,Player player,Inventory inventory,String key){
        return plugin.isEnabled()&&player.isOnline()&&player.getOpenInventory().getTopInventory()==inventory&&plugin.hasBuildingPreview(player.getUniqueId())&&player.hasPermission("craftoui.use")&&player.hasPermission("neverlandtownybuilds.style");
    }
    private static void unavailable(CraftoUIPlugin plugin,Player player,Inventory inventory,String key){player.getScheduler().run(plugin,t->{if(valid(plugin,player,inventory,key))plugin.buildingPreview(player,object("key",key,"available",false));},null);}
    private record SampleBlock(int x,int y,int z,Material material){}
}
