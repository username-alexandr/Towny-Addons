package ru.neverland.townyspecialization.model;
import ru.neverland.integration.SpecializationRules;
public record CityChoice(String specialization,long chosenAt,long nextChangeAt,long revision){
    public CityChoice{if(specialization==null||chosenAt<0||nextChangeAt<chosenAt||revision<0)throw new IllegalArgumentException("Неверная запись специализации");if(specialization.isEmpty()){if(chosenAt!=0||nextChangeAt!=0||revision!=0)throw new IllegalArgumentException("Неверная пустая запись");}else if(!SpecializationRules.PROJECTS.containsValue(specialization)||chosenAt==0||revision==0)throw new IllegalArgumentException("Неизвестная специализация");}
    public static CityChoice empty(){return new CityChoice("",0,0,0);}
}
