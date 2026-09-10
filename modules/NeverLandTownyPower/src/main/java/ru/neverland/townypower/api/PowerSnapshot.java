package ru.neverland.townypower.api;
import ru.neverland.townypower.model.*;
import java.util.*;
public record PowerSnapshot(UUID townId,String townName,boolean paused,String status,PowerEngine.Grid grid,TownPowerState state) {}
