package ru.neverland.mintcontracts.orders;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import ru.neverland.core.SuiteState;
import ru.neverland.mintcontracts.orders.PublicOrders.Template;

/** Each player's draw is committed before it can be offered or accepted. */
public final class PersonalOrderBoards {
    private final Path directory;
    private final List<Template> catalog;
    private final int size;
    private final Map<OrderRarity,Integer> weights;
    private final Clock clock;
    private final Random random;
    private final Map<UUID,DailyOrderBoard> cache = new LinkedHashMap<>(32, .75f, true);

    public PersonalOrderBoards(Path directory,List<Template> catalog,int size,Map<OrderRarity,Integer> weights) throws IOException {
        this(directory,catalog,size,weights,Clock.systemUTC(),new java.security.SecureRandom());
    }

    public PersonalOrderBoards(Path directory,List<Template> catalog,int size,Map<OrderRarity,Integer> weights,Clock clock,Random random) throws IOException {
        this.directory=directory;this.catalog=List.copyOf(catalog);this.size=size;
        this.weights=Map.copyOf(weights);this.clock=clock;this.random=random;
        // Validate selection settings at enable, even before the first player opens /orders.
        DailyOrderBoard.validate(catalog,size,weights);
    }

    public DailyOrderBoard.Snapshot snapshot(UUID player) throws IOException {
        try(var g=SuiteState.guard()) {
            Objects.requireNonNull(player,"player");
            var board=cache.get(player);
            if(board==null) {
                board=new DailyOrderBoard(directory.resolve(player+".yml"),catalog,size,weights,clock,random);
                cache.put(player,board);
                if(cache.size()>256)cache.remove(cache.keySet().iterator().next());
            }
            var snapshot=board.snapshot();
            return snapshot;
        }
    }
}
