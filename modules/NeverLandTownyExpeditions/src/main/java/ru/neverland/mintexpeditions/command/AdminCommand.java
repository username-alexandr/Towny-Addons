package ru.neverland.mintexpeditions.command;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.neverland.core.EffectJournal;
import ru.neverland.mintexpeditions.MintTownyExpeditions;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;

public final class AdminCommand implements CommandExecutor, TabCompleter {
   private final MintTownyExpeditions plugin;

   public AdminCommand(MintTownyExpeditions p) {
      this.plugin = p;
   }

   public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
      if (!s.hasPermission("mintexpeditions.admin")) {
         this.plugin.messages().send(s, "no-permission");
         return true;
      } else if (args.length == 0) {
         this.plugin.messages().list("admin-help").forEach(s::sendMessage);
         return true;
      } else if (args[0].equalsIgnoreCase("payments")) {
         this.plugin
            .service()
            .repository()
            .effects()
            .entries()
            .stream()
            .filter(exx -> exx.state() == EffectJournal.State.PENDING)
            .forEach(exx -> s.sendMessage(exx.id() + " — " + exx.description()));
         return true;
      } else if (!args[0].equalsIgnoreCase("resolve")) {
         if (args[0].equalsIgnoreCase("reload")) {
            this.plugin.reloadAll();
            this.plugin.messages().send(s, "reload");
            return true;
         } else if (args[0].equalsIgnoreCase("list")) {
            s.sendMessage("Активные экспедиции: " + this.plugin.service().repository().active().size());
            this.plugin.service().repository().active().forEach(exx -> s.sendMessage(exx.id() + " — " + exx.definitionId() + " — " + exx.leaderId()));
            return true;
         } else if (args.length < 2) {
            return true;
         } else {
            try {
               UUID id = UUID.fromString(args[1]);
               ActiveExpedition e = this.plugin.service().repository().get(id);
               if (e == null) {
                  this.plugin.messages().send(s, "expedition-not-found", Map.of("id", id));
                  return true;
               }

               if (args[0].equalsIgnoreCase("complete")) {
                  this.plugin.service().finish(e, ExpeditionStatus.COMPLETED);
                  this.plugin.messages().send(s, "admin-completed");
               } else if (args[0].equalsIgnoreCase("cancel")) {
                  this.plugin.service().finish(e, ExpeditionStatus.CANCELLED);
                  this.plugin.messages().send(s, "admin-cancelled");
               }
            } catch (IllegalArgumentException var7) {
               this.plugin.messages().send(s, "expedition-not-found", Map.of("id", args[1]));
            }

            return true;
         }
      } else {
         try {
            if (args.length != 4 || !args[3].equalsIgnoreCase("confirm") || !Set.of("received", "not-received").contains(args[2])) {
               throw new IllegalArgumentException("/townyexpeditions resolve <UUID операции> <received|not-received> confirm");
            }

            this.plugin.service().repository().effects().resolve(UUID.fromString(args[1]), args[2].equals("received"));
            this.plugin.getLogger().warning(s.getName() + " сверил награду " + args[1] + ": " + args[2]);
            s.sendMessage("Решение сохранено. Игрок может повторить /expedition claim.");
         } catch (Exception var8) {
            s.sendMessage("Не удалось выполнить сверку: " + var8.getMessage());
         }

         return true;
      }
   }

   public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if(!s.hasPermission("mintexpeditions.admin"))return List.of();
      if (a.length == 1) {
         return List.of("list", "complete", "cancel", "reload", "payments", "resolve");
      } else {
         return a.length == 2 ? this.plugin.service().repository().active().stream().map(e -> e.id().toString()).toList() : List.of();
      }
   }
}
