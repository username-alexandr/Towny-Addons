package ru.neverland.mintevents.scenarios;

import java.nio.file.*;
import java.util.concurrent.*;
import ru.neverland.core.*;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

/** Open is called from the owning bounded loader, mutations acknowledge disk before publication. */
public final class ScenarioRepository {
    private final Path file;private final AcknowledgedState<State> data;private volatile boolean loaded;
    public ScenarioRepository(Path file){this.file=file;data=new AcknowledgedState<>(file,empty(),state->{String value=ScenarioCodec.encode(state);if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32L*1024*1024)throw new java.io.IOException("Журнал сценариев больше 32 МиБ");return value;},"NeverLand-event-scenario-write");}
    public void load()throws java.io.IOException{if(SuiteState.held())throw new IllegalStateException("Сценарии загружаются вне общей блокировки");boolean exists=Files.exists(file);if(exists&&(Files.size(file)==0||Files.size(file)>32L*1024*1024))throw new java.io.IOException("Журнал сценариев пуст или больше 32 МиБ");var yaml=SafeYaml.load(file);if(exists&&yaml.getKeys(false).isEmpty())throw new java.io.IOException("Пустой журнал сценариев");var next=exists?ScenarioCodec.decode(yaml):empty();data.loaded(next);loaded=true;}
    public State state(){return data.get();}
    public boolean ready(){return loaded;}
    public boolean writable(){return loaded&&data.writable();}
    public boolean busy(){return data.busy();}
    public CompletionStage<State> commit(State before,State next){return data.commit(before,next);}
    public CompletionStage<Void> prepareClose(){return data.prepareClose();}
}
