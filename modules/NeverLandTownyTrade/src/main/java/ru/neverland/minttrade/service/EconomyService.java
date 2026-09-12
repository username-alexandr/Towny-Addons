package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.AccountObserver;
import com.palmergames.bukkit.towny.object.economy.BankAccount;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.PaymentEvidence;
import ru.neverland.integration.TreasuryAccess;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.util.ColorUtil;

public final class EconomyService {
   private final JavaPlugin plugin;
   private final DecimalFormat format = new DecimalFormat("#,##0.##");

   public EconomyService(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean canWithdraw(Town town, double amount) {
      try {
         return town != null && TreasuryAccess.canSpend(town, "infrastructure", amount);
      } catch (RuntimeException var5) {
         return false;
      }
   }

   public boolean withdraw(Town town, double amount, ExportDefinition definition) {
      if (amount <= 0.0) {
         return true;
      } else {
         try {
            return town != null && TreasuryAccess.withdraw(town, "infrastructure", "trade", amount, this.reason("economy.reserve-reason", definition));
         } catch (RuntimeException var6) {
            return false;
         }
      }
   }

   public boolean deposit(Town town, double amount, ExportDefinition definition, String path) {
      if (amount <= 0.0) {
         return true;
      } else {
         try {
            return town != null
               && TreasuryAccess.deposit(
                  town,
                  "infrastructure",
                  path.equals("economy.tariff-reason") ? "tariff" : (path.equals("economy.pending-reason") ? "pending" : "trade"),
                  path.equals("economy.refund-reason"),
                  amount,
                  this.reason(path, definition)
               );
         } catch (RuntimeException var7) {
            return false;
         }
      }
   }

   public double balance(Town town) {
      try {
         return town == null ? 0.0 : town.getAccount().getHoldingBalance();
      } catch (RuntimeException var3) {
         return 0.0;
      }
   }

   public String format(double amount) {
      return this.format.format(amount);
   }

   public boolean transfer(Town town, double amount, ExportDefinition definition, String kind, UUID operation, boolean incoming) {
      if (amount <= 0.0) {
         return true;
      } else if (town == null) {
         return false;
      } else {
         BankAccount account = town.getAccount();
         String token = "[caravan:" + operation + "]";
         final PaymentEvidence evidence = new PaymentEvidence(account, incoming, token, BigDecimal.valueOf(amount).movePointRight(2).longValueExact());
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

            boolean var15;
            try {
               String reason = token
                  + " "
                  + this.reason(
                     incoming
                        ? (kind.equals("refund") ? "economy.refund-reason" : (kind.equals("tariff") ? "economy.tariff-reason" : "economy.sale-reason"))
                        : "economy.reserve-reason",
                     definition
                  );

               try {
                  return evidence.result(
                     incoming
                        ? TreasuryAccess.deposit(town, "infrastructure", kind, kind.equals("refund"), amount, reason)
                        : TreasuryAccess.withdraw(town, "infrastructure", "trade", amount, reason)
                  );
               } catch (RuntimeException var21) {
                  if (!evidence.observed()) {
                     throw var21;
                  }

                  var15 = true;
               }
            } finally {
               account.removeObserver(observer);
            }

            return var15;
         }
      }
   }

   private String reason(String path, ExportDefinition definition) {
      return ColorUtil.strip(
         this.plugin.getConfig().getString(path, "Городская торговля").replace("%export%", definition == null ? "экспорт" : ColorUtil.strip(definition.name()))
      );
   }
}
