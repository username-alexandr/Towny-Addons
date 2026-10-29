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

   public boolean canWithdraw(Town town, double amount) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      try {
         return town != null && TreasuryAccess.canSpend(town, "infrastructure", amount);
      } catch (RuntimeException var5) {
         return false;
      }
   } }

   public boolean withdraw(Town town, double amount, ExportDefinition definition) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      if (amount <= 0.0) {
         return true;
      } else {
         try {
            return town != null && TreasuryAccess.withdraw(town, "infrastructure", "trade", amount, this.reason("economy.reserve-reason", definition));
         } catch (RuntimeException var6) {
            return false;
         }
      }
   } }

   public boolean deposit(Town town, double amount, ExportDefinition definition, String path) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
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
   } }

   public double balance(Town town) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
      try {
         return town == null ? 0.0 : town.getAccount().getHoldingBalance();
      } catch (RuntimeException var3) {
         return 0.0;
      }
   } }

   public String format(double amount) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
      return this.format.format(amount);
   } }

   /** Capture account and reason under the guard; incoming external IO runs on the raw global scheduler. */
   public java.util.function.Supplier<Boolean> prepare(Town town,double amount,ExportDefinition definition,String kind,UUID operation,boolean incoming){try(var guard=ru.neverland.core.SuiteState.guard()){
      if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Неверная сумма каравана");
      if(amount==0)return ()->true;if(town==null||!com.palmergames.bukkit.towny.TownyEconomyHandler.isActive())return ()->false;
      BankAccount account=town.getAccount();if(!incoming&&!TreasuryAccess.canSpend(town,"infrastructure",amount))return ()->false;String token="[caravan:"+operation+"]";
      String reason=token+" "+reason(incoming?(kind.equals("refund")?"economy.refund-reason":kind.equals("tariff")?"economy.tariff-reason":"economy.sale-reason"):"economy.reserve-reason",definition);
      long cents=BigDecimal.valueOf(amount).movePointRight(2).longValueExact();
      return ()->{
         var evidence=new PaymentEvidence(account,incoming,token,cents);var observer=new AccountObserver(){public void withdrew(Account a,double n,String text){evidence.observe(a,false,n,text);}public void deposited(Account a,double n,String text){evidence.observe(a,true,n,text);}};
         // Budget-aware outgoing operations use TreasuryFunding before reaching this native fallback.
         if(incoming)return observed(account,observer,evidence,()->account.deposit(amount,"[NLT|"+(kind.equals("refund")?"refund":"income")+"|infrastructure|"+kind+"] "+reason));
         if(org.bukkit.Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null)throw new IllegalStateException("Установленная казна требует подтверждённого бюджетного платежа");return observed(account,observer,evidence,()->account.withdraw(amount,reason));
      };
   }}
   private static boolean observed(BankAccount account,AccountObserver observer,PaymentEvidence evidence,java.util.function.Supplier<Boolean> transfer){
      synchronized(account){double before=account.getHoldingBalance(true);if(!Double.isFinite(before))throw new IllegalStateException("Баланс банка недоступен");account.addObserver(observer);
         try{boolean reported=transfer.get();if(evidence.observed())return true;throw new IllegalStateException("Банк вернул "+reported+" без точной квитанции; нужна сверка. Совпадение баланса не доказывает отсутствие операции.");}
         catch(RuntimeException ex){if(evidence.observed())return true;throw ex;}finally{account.removeObserver(observer);}
      }
   }
   public boolean transfer(Town town,double amount,ExportDefinition definition,String kind,UUID operation,boolean incoming){return prepare(town,amount,definition,kind,operation,incoming).get();}

   private String reason(String path, ExportDefinition definition) {
      return ColorUtil.strip(
         this.plugin.getConfig().getString(path, "Городская торговля").replace("%export%", definition == null ? "экспорт" : ColorUtil.strip(definition.name()))
      );
   }
}
