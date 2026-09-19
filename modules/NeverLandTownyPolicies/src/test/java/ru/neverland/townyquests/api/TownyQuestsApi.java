package ru.neverland.townyquests.api;
import java.util.*;
public interface TownyQuestsApi extends ru.neverland.core.ApiContract {default Set<String> capabilities(){return Set.of("unlocked");}boolean unlocked(UUID town,String unlock);}
