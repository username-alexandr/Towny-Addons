package ru.neverland.mintcamps;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import ru.neverland.mintcamps.service.HologramService;

public final class HologramLifecycleSmoke {
    public static void main(String[] args) throws Exception {
        if (!Listener.class.isAssignableFrom(HologramService.class)) {
            throw new AssertionError("HologramService не зарегистрирован как Listener");
        }
        if (HologramService.class.getMethod("onChunkLoad", ChunkLoadEvent.class)
                .getAnnotation(EventHandler.class) == null) {
            throw new AssertionError("Обработчик загрузки чанка не отмечен EventHandler");
        }
        if (HologramService.class.getMethod("remove", ru.neverland.mintcamps.model.Camp.class) == null) {
            throw new AssertionError("Отсутствует единый путь удаления голограммы");
        }
        System.out.println("Hologram lifecycle smoke test: OK");
    }
}
