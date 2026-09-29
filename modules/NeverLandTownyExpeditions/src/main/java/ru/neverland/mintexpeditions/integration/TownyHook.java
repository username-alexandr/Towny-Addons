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
   public UUID townId(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) {
      Resident resident = TownyAPI.getInstance().getResident(player);
      Town town = resident == null ? null : resident.getTownOrNull();
      return town == null ? null : town.getUUID();
   } }

   public boolean wilderness(Location location) { synchronized (ru.neverland.core.SuiteState.lock()) {
      return TownyAPI.getInstance().getTown(location) == null;
   } }

   public boolean reward(Player player, double amount, UUID operation) { synchronized (ru.neverland.core.SuiteState.lock()) {
      if (amount <= 0.0) {
         return true;
      } else {
         Resident resident = TownyAPI.getInstance().getResident(player);
         if (resident == null) {
            return false;
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

               boolean var12;
               try {
                  return evidence.result(account.deposit(amount, token + " Награда за экспедицию"));
               } catch (RuntimeException var18) {
                  if (!evidence.observed()) {
                     throw var18;
                  }

                  var12 = true;
               } finally {
                  account.removeObserver(observer);
               }

               return var12;
            }
         }
      }
   } }

   public boolean register(String name, CommandExecutor executor) { synchronized (ru.neverland.core.SuiteState.lock()) {
      return TownyCommandAddonAPI.addSubCommand(CommandType.TOWN, name, executor);
   } }

   public void unregister(String name) { synchronized (ru.neverland.core.SuiteState.lock()) {
      TownyCommandAddonAPI.removeSubCommand(CommandType.TOWN, name);
   } }
}
