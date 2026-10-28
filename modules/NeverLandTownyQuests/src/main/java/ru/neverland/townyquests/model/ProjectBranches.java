package ru.neverland.townyquests.model;
import java.util.*;
public final class ProjectBranches {
    private ProjectBranches(){}
    /** A cancelled run still records the city's choice; cancellation is never a branch reset. */
    public static Optional<Project> alternative(Project project,Collection<CityProject> history){
        if(project.choiceGroup().isEmpty())return Optional.empty();
        return history.stream().map(CityProject::definition).filter(p->p.choiceGroup().equals(project.choiceGroup())&&!p.id().equals(project.id())).findFirst();
    }
    public static void validate(Collection<CityProject> history){var choices=new HashMap<String,String>();for(var run:history){var p=run.definition();if(!p.choiceGroup().isEmpty()){String old=choices.putIfAbsent(p.choiceGroup(),p.id());if(old!=null&&!old.equals(p.id()))throw new IllegalArgumentException("В городе сохранены несовместимые ветви проекта");}}}
}
