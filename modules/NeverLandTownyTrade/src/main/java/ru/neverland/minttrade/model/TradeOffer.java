package ru.neverland.minttrade.model;

import java.util.UUID;
import ru.neverland.minttrade.guarantee.GuaranteeTerms;

public record TradeOffer(UUID id,UUID sellerId,UUID buyerId,String exportId,long createdAt,long expiresAt,GuaranteeTerms guarantee,long quotedBaseCents,String quotedDefinition) {
    public TradeOffer(UUID id,UUID sellerId,UUID buyerId,String exportId,long createdAt,long expiresAt){this(id,sellerId,buyerId,exportId,createdAt,expiresAt,GuaranteeTerms.none(),0,"");}
    public TradeOffer{java.util.Objects.requireNonNull(guarantee);java.util.Objects.requireNonNull(quotedDefinition);if(quotedBaseCents<0||quotedBaseCents>GuaranteeTerms.MAX_CENTS||!quotedDefinition.isEmpty()&&!quotedDefinition.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Неверные условия предложения");}
    public String shortId(){try(var guard=ru.neverland.core.SuiteState.guard()){return id.toString().substring(0,8);}}
}
