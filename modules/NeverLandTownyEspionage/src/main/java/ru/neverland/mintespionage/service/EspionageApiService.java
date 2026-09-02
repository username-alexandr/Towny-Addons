package ru.neverland.mintespionage.service;

import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.mintespionage.api.EspionageSnapshot;
import ru.neverland.mintespionage.api.MintTownyEspionageApi;
import ru.neverland.mintespionage.integration.TownyHook;

import java.util.UUID;

public final class EspionageApiService implements MintTownyEspionageApi {
    private final TownyHook towny;private final EspionageService service;
    public EspionageApiService(TownyHook towny,EspionageService service){this.towny=towny;this.service=service;}
    @Override public EspionageSnapshot snapshot(UUID id){Town town=towny.town(id);if(town==null)return new EspionageSnapshot(0,0,0,0,0,0);var data=service.data(town);return new EspionageSnapshot(data.networkLevel(),data.defenseLevel(),service.repository().active(id).size(),service.activeLimit(town),service.unread(id),service.defenseStrength(town));}
    @Override public double defenseStrength(UUID id){return service.defenseStrength(towny.town(id));}
    @Override public int unreadReports(UUID id){return service.unread(id);}
}
