package ru.neverland.minttrade.model;

import java.util.UUID;

public record TradeHistory(UUID caravanId, UUID sellerId, UUID buyerId, String exportId,
                           int amount, double price, double tariffs, long completedAt, CaravanStatus status,long compensationCents) {
    public TradeHistory(UUID caravanId,UUID sellerId,UUID buyerId,String exportId,int amount,double price,double tariffs,long completedAt,CaravanStatus status){this(caravanId,sellerId,buyerId,exportId,amount,price,tariffs,completedAt,status,0);}
    public TradeHistory{if(compensationCents<0||!Double.isFinite(price)||compensationCents/100.0>price||status!=CaravanStatus.COMPLETED&&compensationCents!=0)throw new IllegalArgumentException("Необеспеченная компенсация в истории");}
}
