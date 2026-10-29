package ru.neverland.mintespionage.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.ApiServices;
import ru.neverland.mintespionage.integration.*;
import ru.neverland.mintespionage.model.SpyOperation;
import static ru.neverland.mintespionage.intelligence.IntelEvidence.*;

/** Reads published APIs and Towny metadata only; never chunks, entities or blocks in another region. */
public final class IntelCollector {
    private static final Map<String,String> BUILDINGS=Map.of("town_hall","Ратуша","forge","Кузница","barracks","Казармы","market","Рынок","miners_guild","Шахтёрская гильдия","temple","Храм","great_library","Великая библиотека","agrarian_complex","Аграрный комплекс");
    private final JavaPlugin plugin;private final TownyHook towny;
    public IntelCollector(JavaPlugin plugin,TownyHook towny){this.plugin=plugin;this.towny=towny;}
    public List<Fact> collect(Town attacker,Town target,SpyOperation operation,Profile profile,long at){
        if(target==null)return List.of(Fact.unknown("city","Город","Towny.towns",at,"Город удалён до наблюдения"));var facts=new ArrayList<Fact>();
        switch(operation.type().toLowerCase(Locale.ROOT)){
            case "treasury"->{
                try{if(!TownyEconomyHandler.isActive())throw new IllegalStateException("Экономика не готова");double value=target.getAccount().getHoldingBalance(true);if(!Double.isFinite(value)||value<0)throw new IllegalStateException("Неверный баланс");long cents=BigDecimal.valueOf(value).setScale(2,RoundingMode.DOWN).movePointRight(2).longValueExact();facts.add(Fact.estimate("treasury","Казна, монет","Towny.economy",at,cents,2,profile,1000));}catch(RuntimeException|LinkageError error){facts.add(Fact.unknown("treasury","Казна, монет","Towny.economy",at,"Экономический провайдер недоступен; это не нулевой баланс"));}
                population(facts,target,at);
            }
            case "military"->{population(facts,target,at);try{var army=ArmyAccess.garrison(target.getUUID());if(army.isEmpty())facts.add(Fact.absent("garrison","Гарнизон","Army.garrison",at,"Отдельный гарнизон не создан или армия не установлена"));else{var g=army.get();numeric(facts,"active","Действующий гарнизон","Army.garrison",g.get("active"),at,0,profile,5);numeric(facts,"capacity","Вместимость гарнизона","Army.garrison",g.get("capacity"),at,0,profile,5);numeric(facts,"reserve","Военный резерв","Army.garrison",g.get("reserve"),at,0,profile,5);numeric(facts,"readiness","Готовность армии, %","Army.garrison",g.get("readiness"),at,2,profile,500);numeric(facts,"score","Военная сила","Army.garrison",g.get("score"),at,2,profile,100);}}catch(RuntimeException|LinkageError error){facts.add(Fact.unknown("garrison","Гарнизон","Army.garrison",at,"Сведения армии временно недоступны"));}
                facts.add(building(target,"barracks",at));facts.add(Fact.estimate("counterintelligence","Контрразведка","Espionage.defenseLevel",at,profile.defense(),0,profile,2));flag(facts,target,"pvp","PvP","isPVP",at);
            }
            case "infrastructure"->{for(String id:new TreeSet<>(BUILDINGS.keySet()))facts.add(building(target,id,at));}
            case "diplomacy"->{try{var nation=target.getNationOrNull();facts.add(Fact.words("nation","Нация","Towny.nation",at,nation==null?"не состоит":nation.getName()));if(nation!=null){facts.add(Fact.words("allies","Союзники нации","Towny.nation.allies",at,names(nation.getAllies())));facts.add(Fact.words("enemies","Противники нации","Towny.nation.enemies",at,names(nation.getEnemies())));}}catch(RuntimeException|LinkageError error){facts.add(Fact.unknown("nation","Нация","Towny.nation",at,"Сведения нации недоступны"));}
                relations(facts,attacker,target,at);flag(facts,target,"open","Открытый город","isOpen",at);
            }
            default->{if(target.getMayor()==null)facts.add(Fact.unknown("mayor","Мэр","Towny.mayor",at,"Мэр в реестре не указан"));else facts.add(Fact.words("mayor","Мэр","Towny.mayor",at,target.getMayor().getName()));population(facts,target,at);facts.add(Fact.exact("claims","Занятые участки","Towny.claims",at,target.getTownBlocks().size(),0));facts.add(Fact.exact("bonus_claims","Бонусные участки","Towny.claims",at,target.getBonusBlocks(),0));var nation=target.getNationOrNull();facts.add(Fact.words("nation","Нация","Towny.nation",at,nation==null?"не состоит":nation.getName()));}
        }return List.copyOf(facts);
    }
    private static void relations(List<Fact> facts,Town attacker,Town target,long at){
        String source="Diplomacy.relations";var api=ApiServices.connect("NeverLandTownyDiplomacy","ru.neverland.townydiplomacy.api.TownyDiplomacyApi",1,"healthy","relations");
        if(api.state()==ApiServices.State.NOT_INSTALLED){facts.add(Fact.absent("relations","Городские договоры",source,at,"Дополнительный реестр договоров не установлен"));return;}
        if(attacker==null||!ru.neverland.core.DiplomacyAccess.available()){facts.add(Fact.unknown("relations","Городские договоры",source,at,"Реестр договоров или город отправителя недоступен"));return;}
        var relations=ru.neverland.core.DiplomacyAccess.relations(attacker.getUUID(),target.getUUID());if(relations.contains("UNAVAILABLE")){facts.add(Fact.unknown("relations","Городские договоры",source,at,"Реестр договоров недоступен"));return;}
        var labels=Map.of("VASSALAGE","вассалитет","ALLIANCE","союз","NONAGGRESSION","ненападение","GUARANTEE","гарантия независимости","EMBARGO","эмбарго","SANCTIONS","санкции","TRADE","торговый договор");
        if(!labels.keySet().containsAll(relations)){facts.add(Fact.unknown("relations","Городские договоры",source,at,"В реестре появились неизвестные типы договоров"));return;}
        facts.add(Fact.words("relations","Городские договоры",source,at,relations.isEmpty()?"действующие договоры не зарегистрированы":relations.stream().sorted().map(labels::get).collect(java.util.stream.Collectors.joining(", "))));
    }
    private static void population(List<Fact> facts,Town target,long at){facts.add(Fact.exact("population","Жителей","Towny.residents",at,target.getResidents().size(),0));}
    private static void numeric(List<Fact> facts,String key,String label,String source,Object raw,long at,int scale,Profile profile,long minimum){try{if(!(raw instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()<0)throw new IllegalArgumentException();long value=new BigDecimal(number.toString()).setScale(scale,RoundingMode.DOWN).movePointRight(scale).longValueExact();facts.add(Fact.estimate(key,label,source,at,value,scale,profile,minimum));}catch(RuntimeException error){facts.add(Fact.unknown(key,label,source,at,"Поле источника недоступно или имеет неверный формат"));}}
    private Fact building(Town target,String id,long at){String source="Builds.operationalLevel",label=BUILDINGS.getOrDefault(id,id);try{var api=ApiServices.require(plugin.getConfig().getString("buildings.plugin","NeverLandTownyBuilds"),"ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel");Object raw=api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},target.getUUID(),id);if(!(raw instanceof Integer value)||value<0||value>100)throw new IllegalArgumentException();return Fact.exact("building."+id,label,source,at,value,0);}catch(ReflectiveOperationException|RuntimeException|LinkageError error){return Fact.unknown("building."+id,label,source,at,"Сведения о работающем уровне недоступны; это не отсутствие здания");}}
    private static void flag(List<Fact> facts,Town target,String key,String label,String method,long at){try{Object result=target.getClass().getMethod(method).invoke(target);if(!(result instanceof Boolean value))throw new IllegalArgumentException();facts.add(Fact.words(key,label,"Towny."+method,at,value?"да":"нет"));}catch(ReflectiveOperationException|RuntimeException error){facts.add(Fact.unknown(key,label,"Towny."+method,at,"Настройку города не удалось прочитать"));}}
    private static String names(Collection<? extends com.palmergames.bukkit.towny.object.Nation> values){var list=values.stream().map(com.palmergames.bukkit.towny.object.Nation::getName).sorted().toList();if(list.isEmpty())return "нет";String joined=String.join(", ",list);return joined.length()<=580?joined:joined.substring(0,560)+"… (список сокращён)";}
}
