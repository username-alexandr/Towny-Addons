package ru.neverland.townydistricts.api;

import java.util.UUID;

/** Immutable map data. Coordinates use Towny's plot grid; blockSize is normally 16. */
public record MobFarmPlot(UUID townId, UUID worldId, String worldName, int plotX, int plotZ,
                          int blockSize, boolean mobsAllowed, String status, String description) {
    public long minBlockX(){return (long)plotX*blockSize;}
    public long minBlockZ(){return (long)plotZ*blockSize;}
    public long maxBlockX(){return minBlockX()+blockSize-1;}
    public long maxBlockZ(){return minBlockZ()+blockSize-1;}
    public boolean protectionDisabled(){return status.equals("ALLOWED")||status.equals("FORCED_ALLOWED")||status.equals("TOWNY_DISABLED");}
}
