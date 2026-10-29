package ru.neverland.townybuilds.util;

import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.Base64;

public final class ItemCodec {
    private static final String COUNTED="nlt-counted-v1:";
    private static final int MAX_COUNT=1_000_000_000;
    private ItemCodec() {
    }

    public static String encode(ItemStack[] contents) throws IOException { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        try {
            return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось сериализовать предметы", exception);
        }
    } }

    public static ItemStack[] decode(String encoded, int fallbackSize) throws IOException, ClassNotFoundException { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[fallbackSize];
        }
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось десериализовать предметы", exception);
        }
    } }

    public static String encodeSingle(ItemStack item) throws IOException { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        try {
            if(item==null||item.getType().isAir()||item.getAmount()<1||item.getAmount()>MAX_COUNT)throw new IllegalArgumentException("Неверный предмет или его количество");
            int amount=item.getAmount();var sample=item.clone();sample.setAmount(amount>99?1:amount);
            String encoded=Base64.getEncoder().encodeToString(sample.serializeAsBytes());
            return amount>99?COUNTED+amount+":"+encoded:encoded;
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось сериализовать предмет", exception);
        }
    } }

    public static ItemStack decodeSingle(String encoded) throws IOException, ClassNotFoundException { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        try {
            if(encoded!=null&&encoded.startsWith(COUNTED)){
                int split=encoded.indexOf(':',COUNTED.length());if(split<0)throw new IllegalArgumentException("Нет образца предмета");
                String count=encoded.substring(COUNTED.length(),split);if(!count.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException("Неверное количество предмета");
                int amount=Integer.parseInt(count);if(amount>MAX_COUNT)throw new IllegalArgumentException("Слишком много предметов");
                var sample=ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded.substring(split+1)));if(sample.getType().isAir()||sample.getAmount()!=1)throw new IllegalArgumentException("Неверный образец предмета");sample.setAmount(amount);return sample;
            }
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException exception) {
            throw new IOException("Не удалось десериализовать предмет", exception);
        }
    } }
}
