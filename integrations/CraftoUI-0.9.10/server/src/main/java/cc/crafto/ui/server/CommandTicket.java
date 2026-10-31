package cc.crafto.ui.server;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** One outstanding review per connection. Confirmation cannot replace its arguments. */
final class CommandTicket<T> {
    private String token="";private long until;private T value;
    String issue(T value,long now){this.value=value;until=now+TimeUnit.SECONDS.toNanos(30);return token=UUID.randomUUID().toString();}
    T consume(String presented,long now){if(token.isEmpty()||!token.equals(presented))return null;T result=now<=until?value:null;clear();return result;}
    void clear(){token="";value=null;until=0;}
}
