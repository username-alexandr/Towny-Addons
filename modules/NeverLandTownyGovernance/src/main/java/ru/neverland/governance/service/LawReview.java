package ru.neverland.governance.service;
import java.math.BigDecimal;
import java.util.*;
import ru.neverland.governance.model.*;

/** Human-readable current-to-proposed values; no forecast revenue or invented bank movement. */
public final class LawReview {
    private LawReview(){}
    public static List<String> lines(LawImpact.Projection p,java.util.function.Function<String,String> lawName){
        var lines=new ArrayList<String>();lines.add("После принятия решения:");
        lines.add("Городской налог Towny: "+tax(p.before())+" → "+tax(p.after()));
        lines.add("Стоимость стройки: "+percent(p.current().construction())+" → "+percent(p.proposed().construction()));
        lines.add("Стоимость идеологии: "+percent(p.current().ideologyCost())+" → "+percent(p.proposed().ideologyCost()));
        lines.add("Опыт идеологии: "+percent(p.current().ideologyExperience())+" → "+percent(p.proposed().ideologyExperience()));
        lines.add("Дополнительные участки: "+p.before().bonusBlocks()+" → "+p.after().bonusBlocks());
        var replaced=p.removed().stream().filter(id->p.after().laws().stream().noneMatch(id::equals)).map(lawName).toList();if(!replaced.isEmpty())lines.add("Перестанут действовать: "+String.join(", ",replaced));
        lines.addAll(p.warnings());lines.add("100% — обычное влияние законов. Другие бонусы не включены.");lines.add("Это текущая оценка; до принятия условия могут измениться.");lines.add("Отдельные сборы Taxes и будущие доходы здесь не рассчитаны.");return List.copyOf(lines);
    }
    private static String tax(LawImpact.City city){return number(city.tax())+(city.percentage()?"% средств жителя":" монет с жителя");}
    private static String percent(double value){return number(value*100)+"%";}
    private static String number(double value){return BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
}
