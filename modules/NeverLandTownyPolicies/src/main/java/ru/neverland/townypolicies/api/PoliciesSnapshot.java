package ru.neverland.townypolicies.api;
import java.util.*;
import ru.neverland.townypolicies.model.CityPolicies;
public record PoliciesSnapshot(UUID townId,String townName,CityPolicies state,Map<String,String> status){public PoliciesSnapshot{status=Map.copyOf(status);}}
