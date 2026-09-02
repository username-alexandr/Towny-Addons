package ru.neverland.townybuilds.util;

import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.Base64;

public final class ItemCodec {
    private ItemCodec() {
    }

    public static String encode(ItemStack[] contents) throws IOException {
        try {
            return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось сериализовать предметы", exception);
        }
    }

    public static ItemStack[] decode(String encoded, int fallbackSize) throws IOException, ClassNotFoundException {
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[fallbackSize];
        }
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось десериализовать предметы", exception);
        }
    }

    public static String encodeSingle(ItemStack item) throws IOException {
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось сериализовать предмет", exception);
        }
    }

    public static ItemStack decodeSingle(String encoded) throws IOException, ClassNotFoundException {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось десериализовать предмет", exception);
        }
    }
}
