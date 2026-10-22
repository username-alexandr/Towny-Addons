package ru.neverland.townybuilds.api;

import java.util.UUID;

/** One blueprint position, never a full-height footprint or a bounding-box excavation. */
public record RegisteredBuildingBlock(UUID worldId,int x,int y,int z,String project,String material) {}
