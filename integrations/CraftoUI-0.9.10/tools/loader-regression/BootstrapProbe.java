package cc.crafto.ui.probe;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** TEST ONLY. Exit before the game window; exercises actual Knot discovery and mixin setup. */
public final class BootstrapProbe implements PreLaunchEntrypoint {
    @Override public void onPreLaunch(){
        try{
            FabricLoader loader=FabricLoader.getInstance();
            for(String[] required:new String[][]{{"fabricloader","0.19.4"},{"craftoui","0.9.9"},{"mixinextras","0.5.5"},{"minecraft","26.2"}}){
                String version=loader.getModContainer(required[0]).orElseThrow().getMetadata().getVersion().getFriendlyString();
                if(!version.equals(required[1]))throw new AssertionError(required[0]+"="+version);
                System.out.println("PASS actual Knot bootstrap selected "+required[0]+" "+version);
            }
            ClassLoader knot=Thread.currentThread().getContextClassLoader();
            Class<?> extras=Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap",true,knot);
            if(!extras.getMethod("getVersion").invoke(null).equals("0.5.5"))throw new AssertionError("Wrong MixinExtras class loaded");
            extras.getMethod("init").invoke(null);
            Class<?> handler=Class.forName("net.minecraft.client.gui.components.events.ContainerEventHandler",false,knot);
            if(!handler.getClassLoader().getClass().getName().contains("KnotClassLoader"))throw new AssertionError("Game class bypassed Knot");
            System.out.println("PASS loaded MixinExtras 0.5.5 implementation; game class owned by KnotClassLoader");
            System.out.println("PASS ContainerEventHandler transformed before the game window");
            System.out.println("PASS TEST-ONLY bootstrap complete; full game was not started");
            System.out.flush();System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
}
