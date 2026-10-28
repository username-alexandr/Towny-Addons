package ru.neverland.townybuilds.api;
import java.util.*;
/** Immutable material requirements after subtracting the project's fund and existing warehouse stock. */
public interface BuildingMaterialsApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("needs");}
    Map<String,Object> needs(UUID town,String project);
}
