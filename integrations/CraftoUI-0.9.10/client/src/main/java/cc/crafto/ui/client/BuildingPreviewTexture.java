package cc.crafto.ui.client;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import static cc.crafto.ui.Protocol.*;
/** Bounded server schematic images; no filesystem paths, URLs, model deserialization or network fetches. */
final class BuildingPreviewTexture implements AutoCloseable {
    private final Identifier[] textures=new Identifier[4];private final int[] sizes=new int[4];private int view;
    void accept(JsonObject body){
        int index=integer(body,"view",-1);if(index<0||index>3||!body.has("chunks"))return;
        var chunks=body.getAsJsonArray("chunks");if(chunks.size()>6)return;var text=new StringBuilder();for(var c:chunks){if(!c.isJsonPrimitive()||c.getAsString().length()>4000)return;text.append(c.getAsString());}
        try{
            byte[] bytes=Base64.getDecoder().decode(text.toString());if(bytes.length<24||bytes.length>16000)return;
            ByteBuffer header=ByteBuffer.wrap(bytes);if(header.getLong()!=0x89504e470d0a1a0aL||header.getInt()!=13||header.getInt()!=0x49484452)return;
            int width=header.getInt(),height=header.getInt();if(width!=height||width<64||width>256)return;
            NativeImage image=NativeImage.read(new ByteArrayInputStream(bytes));if(image.getWidth()!=width||image.getHeight()!=height){image.close();return;}
            Identifier id=Identifier.fromNamespaceAndPath("craftoui","building_preview/"+UUID.randomUUID());
            var texture=new DynamicTexture(()->"CraftoUI building schematic",image);
            try{Minecraft.getInstance().getTextureManager().register(id,texture);}catch(RuntimeException failure){texture.close();throw failure;}
            if(textures[index]!=null)Minecraft.getInstance().getTextureManager().release(textures[index]);textures[index]=id;sizes[index]=width;
        }catch(Exception ignored){}
    }
    boolean ready(){return textures[view]!=null;}
    void rotate(){view=(view+1)%4;}
    void draw(GuiGraphicsExtractor g,int x,int y,int size){if(ready())g.blit(RenderPipelines.GUI_TEXTURED,textures[view],x,y,0,0,size,size,sizes[view],sizes[view],sizes[view],sizes[view]);}
    @Override public void close(){for(int i=0;i<4;i++)if(textures[i]!=null){Minecraft.getInstance().getTextureManager().release(textures[i]);textures[i]=null;}}
}
