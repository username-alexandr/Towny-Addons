package ru.neverland.townytaxes.integration;

import com.palmergames.bukkit.towny.object.Resident;import com.palmergames.bukkit.towny.object.Town;import me.clip.placeholderapi.expansion.PlaceholderExpansion;import org.bukkit.OfflinePlayer;import org.bukkit.entity.Player;import org.jetbrains.annotations.NotNull;import org.jetbrains.annotations.Nullable;
import ru.neverland.townytaxes.NeverLandTownyTaxes;import ru.neverland.townytaxes.model.Domain;import ru.neverland.townytaxes.service.FiscalService;
import java.util.Locale;

public final class TaxesExpansion extends PlaceholderExpansion {
    private final NeverLandTownyTaxes plugin;private final TownyHook towny;private final FiscalService fiscal;public TaxesExpansion(NeverLandTownyTaxes p,TownyHook t,FiscalService f){plugin=p;towny=t;fiscal=f;}
    @Override public @NotNull String getIdentifier(){return"townytaxes";}@Override public @NotNull String getAuthor(){return"Alexander Sokolov";}@Override public @NotNull String getVersion(){return plugin.getPluginMeta().getVersion();}@Override public boolean persist(){return true;}
    @Override public @Nullable String onRequest(OfflinePlayer offline,@NotNull String params){String key=params.toLowerCase(Locale.ROOT);if(key.equals("server_reserve"))return fiscal.format(fiscal.serverReserve());if(!(offline instanceof Player player))return"";Resident resident=towny.resident(player);Town town=towny.town(player);return switch(key){case"debt"->fiscal.format(resident==null?0:fiscal.taxDebt(resident.getUUID()));case"town_debt"->fiscal.format(town==null?0:fiscal.taxDebt(town.getUUID()));case"town_policies"->String.valueOf(town==null?0:fiscal.repository().policies().stream().filter(v->v.scope()==Domain.Scope.TOWN&&v.targetId().equals(town.getUUID())).count());case"town_sanctions"->String.valueOf(town==null?0:fiscal.repository().sanctions().stream().filter(v->v.active(System.currentTimeMillis())&&v.targetScope()==Domain.Scope.TOWN&&v.targetId().equals(town.getUUID())).count());case"tax_multiplier"->String.valueOf(resident==null?1:fiscal.taxMultiplier(new TownyHook.Party(Domain.Scope.PLAYER,resident.getUUID(),resident.getName(),resident.getAccount())));default->null;};}
}
