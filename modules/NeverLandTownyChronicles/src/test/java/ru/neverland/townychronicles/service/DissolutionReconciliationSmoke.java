package ru.neverland.townychronicles.service;

import ru.neverland.townychronicles.model.ChronicleCategory;
import ru.neverland.townychronicles.model.ChronicleEntry;

import java.util.List;
import java.util.UUID;

/** A failed async callback may have saved the entry without the inactive state. */
public final class DissolutionReconciliationSmoke {
    public static void main(String[] args) {
        UUID town=UUID.randomUUID();
        ChronicleEntry previous=new ChronicleEntry(UUID.randomUUID(),town,"Город",ChronicleCategory.DISSOLUTION,
                200,"Город Город исчез",List.of("Причина: UPKEEP"),"","Towny");
        check(ChronicleTracker.recordedDeletion(List.of(previous),previous.title(),100),"Повторное удаление после старого сбоя");
        check(!ChronicleTracker.recordedDeletion(List.of(previous),previous.title(),300),"Новое удаление после восстановления");
        check(!ChronicleTracker.recordedDeletion(List.of(previous),"Город Другой исчез",100),"Изменённое имя города");
        ChronicleEntry unrelated=new ChronicleEntry(UUID.randomUUID(),town,"Город",ChronicleCategory.DISSOLUTION,
                200,previous.title(),List.of(),"","Admin");
        check(!ChronicleTracker.recordedDeletion(List.of(unrelated),previous.title(),100),"Ручная запись");
        System.out.println("DissolutionReconciliationSmoke OK");
    }
    private static void check(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
}
