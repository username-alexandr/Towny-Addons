package ru.neverland.townydistricts.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import ru.neverland.core.DerivedViewCache;
import ru.neverland.townydistricts.model.DistrictAssessment.*;

/** One bounded CPU worker, immutable inputs and no Bukkit, Towny, disk or SuiteState on that worker. */
public final class DistrictPlanner {
    public record Reading(String status,Result result,long at,String issue){}
    private final DerivedViewCache<Input,Result> views;
    public DistrictPlanner(){this(ru.neverland.townydistricts.model.DistrictAssessment::calculate);}
    DistrictPlanner(Function<Input,Result> calculate){this(calculate,error->{});}
    DistrictPlanner(Function<Input,Result> calculate,Consumer<Throwable> diagnostic){views=new DerivedViewCache<>("NeverLand-district-assessment",calculate,diagnostic);}
    public Reading read(UUID town,Input input){var read=views.read(town,input);return new Reading(read.status(),read.result(),read.at(),read.issue());}
    public CompletionStage<Void> prepareClose(){return views.prepareClose();}public void awaitClosed(){views.awaitClosed();}
}
