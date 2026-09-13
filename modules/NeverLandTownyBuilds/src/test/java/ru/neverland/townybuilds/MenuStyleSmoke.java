package ru.neverland.townybuilds;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import ru.neverland.core.MenuStyle;

public final class MenuStyleSmoke {
    private static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private static void check(boolean b, String why) { if (!b) throw new AssertionError(why); }
    public static void main(String[] args) {
        Component title = MenuStyle.title(MenuStyle.decode("&#241A33Казна · бюджет"), "#F4F7FC");
        check(title.color().equals(TextColor.color(0xF4F7FC)), "dark configured title replaced");
        check(plain(title).equals("Казна · бюджет"), "UTF title retained");
        check(MenuStyle.title(title, "#273247").color().equals(TextColor.color(0x273247)), "vanilla background override");
        String name = MenuStyle.nameLegacy("§x§2§4§1§A§3§3Торговый центр");
        check(plain(MenuStyle.decode(name)).equals("Торговый центр"), "expanded hex round trip");
        check(plain(MenuStyle.nameComponent(Component.text(" "))).equals(" "), "blank filler");
        var lore = MenuStyle.loreComponents(List.of(MenuStyle.decode("&0Длинное описание предмета помогает жителю понять назначение здания и условия работы городской службы."), MenuStyle.decode("&8ЛКМ — открыть")));
        check(lore.size() >= 4, "long prose wraps and actions separate");
        for (Component line : lore) {
            check(plain(line).length() <= 44, "readable line width");
            if (!plain(line).isEmpty()) check(line.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE, "no inherited italics");
        }
        check(plain(lore.get(lore.size()-2)).isEmpty() && plain(lore.get(lore.size()-1)).startsWith("→ "), "action paragraph");
        String id = "aabbccdd-1234-4567-8901-aabbccddeeff";
        check(MenuStyle.loreStrings(List.of("Идентификатор операции: " + id)).stream().anyMatch(s -> s.contains(id)), "IDs stay intact");
        var translated = MenuStyle.loreComponents(List.of(Component.translatable("block.minecraft.oak_planks")));
        check(translated.get(0) instanceof net.kyori.adventure.text.TranslatableComponent, "client translation preserved");
        System.out.println("MenuStyleSmoke PASS");
    }
}
