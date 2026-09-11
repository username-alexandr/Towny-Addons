package ru.neverland.townybuilds.api;
import java.util.UUID;
/** Physical completed building footprint with foundation height for city professions. */
public record BuildingWorkplace(UUID worldId,int minX,int minZ,int maxX,int maxZ,int y,int completedLevel){}
