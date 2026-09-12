package ru.neverland.core;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class PlayerSaveReceipt {
   private final Path file;
   private final byte[] before;

   private PlayerSaveReceipt(Path file, byte[] before) {
      this.file = file;
      this.before = before;
   }

   public static PlayerSaveReceipt before(Player player) {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Сохранение игрока требует основного потока");
      } else {
         Path root = ((World)Bukkit.getWorlds().get(0)).getWorldFolder().toPath();
         Path dimensions = root.getParent() == null ? null : root.getParent().getParent();
         if (dimensions != null && dimensions.getFileName() != null && dimensions.getFileName().toString().equals("dimensions")) {
            root = dimensions.getParent();
         }

         Path directory = Files.isDirectory(root.resolve("players/data")) ? root.resolve("players/data") : root.resolve("playerdata");
         Path file = directory.resolve(player.getUniqueId() + ".dat");

         try {
            return new PlayerSaveReceipt(file, Files.exists(file) ? digest(file) : null);
         } catch (IOException var6) {
            throw new UncheckedIOException("Нельзя проверить исходные данные игрока", var6);
         }
      }
   }

   public void save(Player player) {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Сохранение игрока требует основного потока");
      } else {
         player.saveData();

         try {
            if (Files.isRegularFile(this.file) && !Arrays.equals(this.before, digest(this.file))) {
               try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(this.file))) {
                  input.transferTo(OutputStream.nullOutputStream());
               }

               try (FileChannel var10 = FileChannel.open(this.file, StandardOpenOption.WRITE)) {
                  var10.force(true);
               }
            } else {
               throw new IOException("Нет подтверждения записи изменённого инвентаря игрока");
            }
         } catch (IOException var9) {
            throw new UncheckedIOException("Выдача/приём предметов требует сверки: сохранение игрока не подтверждено", var9);
         }
      }
   }

   private static byte[] digest(Path file) throws IOException {
      try {
         MessageDigest hash = MessageDigest.getInstance("SHA-256");

         try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), hash)) {
            input.transferTo(OutputStream.nullOutputStream());
         }

         return hash.digest();
      } catch (NoSuchAlgorithmException var7) {
         throw new AssertionError(var7);
      }
   }
}
