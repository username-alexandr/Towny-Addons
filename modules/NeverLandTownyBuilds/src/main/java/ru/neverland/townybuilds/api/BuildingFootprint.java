package ru.neverland.townybuilds.api;
import java.util.UUID;
/** Full horizontal bounding rectangle, including roof overhangs and courtyard. */
public record BuildingFootprint(UUID worldId,int minX,int minZ,int maxX,int maxZ,int completedLevel,int requiredLevel){}
