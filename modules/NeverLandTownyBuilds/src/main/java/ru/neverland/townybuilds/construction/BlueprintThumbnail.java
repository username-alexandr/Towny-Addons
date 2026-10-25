package ru.neverland.townybuilds.construction;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/** Isometric schematic of the actual plan. Immutable voxels render asynchronously, without Bukkit world access. */
public final class BlueprintThumbnail {
    public record Voxel(int x,int y,int z,int rgb,double height){}
    private final Plugin plugin;
    private final Map<BlueprintPlan,CompletableFuture<Map<String,String>>> cache=new LinkedHashMap<>();
    public BlueprintThumbnail(Plugin plugin){this.plugin=plugin;}
    public synchronized CompletableFuture<Map<String,String>> preview(BlueprintPlan plan){
        if(cache.containsKey(plan))return cache.get(plan);
        if(plan.blocks().size()>100000)return CompletableFuture.completedFuture(Map.of());
        var colors=new HashMap<Material,Integer>();List<Voxel> voxels=new ArrayList<>();
        for(var entry:plan.blocks().entrySet()){
            var b=entry.getValue();var o=entry.getKey();int color=colors.computeIfAbsent(b.material(),m->{int c=m.createBlockData().getMapColor().asRGB();return c==0?0x98aab5:c;});
            String material=b.material().name();double h=material.endsWith("_SLAB")?.5:material.endsWith("_CARPET")?.0625:1;
            voxels.add(new Voxel(o.x(),o.y(),o.z(),color,h));
        }
        var immutable=List.copyOf(voxels);var future=new CompletableFuture<Map<String,String>>();cache.put(plan,future);
        while(cache.size()>32)cache.remove(cache.keySet().iterator().next());
        org.bukkit.Bukkit.getAsyncScheduler().runNow(plugin,t->{try{Map<String,String> images=new LinkedHashMap<>();for(int angle=0;angle<4;angle++){
            byte[] png=render(immutable,angle,192);if(png.length>16000)png=render(immutable,angle,128);
            if(png.length>16000)throw new IllegalArgumentException("Слишком большой предпросмотр");images.put(Integer.toString(angle),Base64.getEncoder().encodeToString(png));
        }future.complete(Map.copyOf(images));}catch(Throwable error){future.completeExceptionally(error);}});
        return future;
    }
    public synchronized void clear(){cache.values().forEach(f->f.cancel(false));cache.clear();}
    public static byte[] render(List<Voxel> input,int angle,int size)throws Exception{
        if(size<64||size>256||input.size()>100000)throw new IllegalArgumentException("Preview bounds");
        List<Voxel> voxels=new ArrayList<>();for(var v:input){int x=v.x(),z=v.z();for(int n=0;n<Math.floorMod(angle,4);n++){int q=x;x=-z;z=q;}voxels.add(new Voxel(x,v.y(),z,v.rgb(),v.height()));}
        double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=Double.POSITIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
        for(var b:voxels)for(double x:new double[]{b.x(),b.x()+1})for(double z:new double[]{b.z(),b.z()+1})for(double y:new double[]{b.y(),b.y()+b.height()}){double u=(x-z)*.866,v=(x+z)*.5-y;minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);}
        var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(!voxels.isEmpty()){
            double scale=Math.min((size-16)/Math.max(1,maxU-minU),(size-16)/Math.max(1,maxV-minV));double ox=(size-(maxU-minU)*scale)/2-minU*scale,oy=(size-(maxV-minV)*scale)/2-minV*scale;
            voxels.sort(Comparator.comparingDouble(v->v.x()+v.z()+v.y()));
            for(var b:voxels){double x=b.x(),y=b.y(),z=b.z(),h=b.height();int c=b.rgb();
                face(g,new double[][]{{x+1,y,z},{x+1,y,z+1},{x+1,y+h,z+1},{x+1,y+h,z}},c,.72,scale,ox,oy);
                face(g,new double[][]{{x,y,z+1},{x+1,y,z+1},{x+1,y+h,z+1},{x,y+h,z+1}},c,.87,scale,ox,oy);
                face(g,new double[][]{{x,y+h,z},{x+1,y+h,z},{x+1,y+h,z+1},{x,y+h,z+1}},c,1.12,scale,ox,oy);
            }
        }
        g.dispose();var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    private static void face(Graphics2D g,double[][] vertices,int color,double light,double scale,double ox,double oy){
        var polygon=new java.awt.geom.Path2D.Double();for(int i=0;i<vertices.length;i++){var p=vertices[i];double u=(p[0]-p[2])*.866*scale+ox,v=((p[0]+p[2])*.5-p[1])*scale+oy;if(i==0)polygon.moveTo(u,v);else polygon.lineTo(u,v);}polygon.closePath();
        g.setColor(new Color(Math.min(255,(int)(((color>>16)&255)*light)),Math.min(255,(int)(((color>>8)&255)*light)),Math.min(255,(int)((color&255)*light))));g.fill(polygon);
    }
}
