package ru.neverland.townyspecialization.production;

import java.util.*;

/** SQL issues revisions in worker order; delayed callbacks cannot overwrite a newer committed snapshot. */
final class ProfileGoodsCache {
    private long revision;private volatile List<Map<String,Object>> active=List.of();
    synchronized void publish(ProductionRepository.GoodsSnapshot snapshot){
        if(snapshot.revision()<=revision)return;
        active=snapshot.rows().stream().map(ProfileGoodsBook.Entry::view).toList();revision=snapshot.revision();
    }
    List<Map<String,Object>> active(){return active;}
}
