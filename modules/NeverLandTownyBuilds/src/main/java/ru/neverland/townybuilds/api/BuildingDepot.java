package ru.neverland.townybuilds.api;
import java.util.UUID;
public record BuildingDepot(String id,String name,String icon,UUID world,int minX,int minZ,int maxX,int maxZ,int y,int level,int slots) {}
