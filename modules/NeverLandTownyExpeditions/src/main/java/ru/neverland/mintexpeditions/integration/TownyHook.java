package ru.neverland.mintexpeditions.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.AccountObserver;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import ru.neverland.core.PaymentEvidence;

public final class TownyHook {
   public record Payout(boolean accepted, String reason) {
      public static Payout paid() { return new Payout(true, ""); }
      public static Payout deferred(String reason) { return new Payout(false, reason); }
   }
   public UUID townId(Player player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      Resident resident = TownyAPI.getInstance().getResident(player);
      Town town = resident == null ? null : resident.getTownOrNull();
      return town == null ? null : town.getUUID();
   } }

   public boolean wilderness(Location location) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      return TownyAPI.getInstance().getTown(location) == null;
   } }

   public Payout reward(Player player, double amount, UUID operation) {
      if(ru.neverland.core.SuiteState.held())throw new IllegalStateException("Банк должен вызываться вне Suite");
      if(!com.palmergames.bukkit.towny.TownyEconomyHandler.isActive()||com.palmergames.bukkit.towny.TownyEconomyHandler.activeAdapter()==null)
         return Payout.deferred("Экономика Towny не подключена к работающему провайдеру; проверьте Towny и Vault");
      if (amount <= 0.0) {
         return Payout.paid();
      } else {
         Resident resident = TownyAPI.getInstance().getResident(player);
         if (resident == null) {
            return Payout.deferred("Towny пока не видит счёт игрока");
         } else {
            Account account = resident.getAccount();
            String token = "[expedition:" + operation + "]";
            final PaymentEvidence evidence = new PaymentEvidence(account, true, token, BigDecimal.valueOf(amount).movePointRight(2).longValueExact());
            var observer = new AccountObserver() {
               @Override
               public void withdrew(Account source, double value, String reason) { 
                  evidence.observe(source, false, value, reason);
                }

               @Override
               public void deposited(Account source, double value, String reason) { 
                  evidence.observe(source, true, value, reason);
                }
            };
            synchronized (account) {
               account.addObserver(observer);

               boolean observed;
               try {
                  observed = evidence.result(account.deposit(amount, token + " Награда за экспедицию"));
               } catch (RuntimeException var18) {
                  if (!evidence.observed()) {
                     throw var18;
                  }

                  observed = true;
               } finally {
                  account.removeObserver(observer);
               }

               return observed ? Payout.paid() : Payout.deferred("Банк отклонил перевод; проверьте экономический провайдер");
            }
         }
      }
   }

   public boolean register(String name, CommandExecutor executor) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
      return TownyCommandAddonAPI.addSubCommand(CommandType.TOWN, name, executor);
   } }

   public void unregister(String name) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
      TownyCommandAddonAPI.removeSubCommand(CommandType.TOWN, name);
   } }
}
