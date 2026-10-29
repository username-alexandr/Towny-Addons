package ru.neverland.minttrade.integration;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import ru.neverland.core.ApiServices;

/** One immutable budget request. PREPARED is an acknowledgement of intent, never payment. */
public final class TreasuryFunding {
    public static final String OWNER="NeverLandTownyTrade";
    public enum Result { WAIT, PAID, NOT_APPLIED, REVIEW }
    private static final class ReceiptMismatch extends IllegalStateException{ReceiptMismatch(String message){super(message);}}
    public record Terms(UUID operation,UUID town,UUID actor,long cents,String category,String source,String reason){
        public Terms{Objects.requireNonNull(operation);Objects.requireNonNull(town);Objects.requireNonNull(actor);Objects.requireNonNull(category);Objects.requireNonNull(source);Objects.requireNonNull(reason);if(cents<1||cents>9_000_000_000_000_000L||!Set.of("army","construction","infrastructure","social","free").contains(category)||source.isEmpty()||source.length()>64||reason.isEmpty()||reason.length()>512)throw new IllegalArgumentException("Неверные условия бюджетного платежа торговли");}
        public void verify(Map<String,Object> receipt,UUID id,boolean refund,String refundReason){
            if(!Objects.equals(receipt.get("operation"),id.toString())||!Objects.equals(receipt.get("town"),town.toString())||!Objects.equals(receipt.get("actor"),actor.toString())||!Objects.equals(receipt.get("category"),category)||!Objects.equals(receipt.get("source"),source)||!Objects.equals(receipt.get("reason"),refund?refundReason:reason)||!Objects.equals(receipt.get("owner"),OWNER)||!Objects.equals(receipt.get("kind"),refund?"REFUND":"DEBIT")||!Objects.equals(receipt.get("backingDebit"),refund?operation.toString():"")||!(receipt.get("cents") instanceof Number amount)||new java.math.BigDecimal(amount.toString()).compareTo(java.math.BigDecimal.valueOf(cents))!=0)throw new ReceiptMismatch("Квитанция казны не совпадает с сохранёнными условиями торговли");
        }
        public Result result(Map<String,Object> receipt,UUID id,boolean refund,String refundReason){
            if(Objects.equals(receipt.get("status"),"ABSENT")){if(!Objects.equals(receipt.get("operation"),id.toString()))throw new ReceiptMismatch("Ответ об отсутствии относится к другой операции");return Result.WAIT;}verify(receipt,id,refund,refundReason);
            return switch(String.valueOf(receipt.get("status"))){case "PAID"->refund||Boolean.TRUE.equals(receipt.get("backingAvailable"))?Result.PAID:Result.REVIEW;case "REJECTED"->Result.NOT_APPLIED;case "REVIEW"->Result.REVIEW;case "PREPARED","INVOKING"->Result.WAIT;default->throw new ReceiptMismatch("Неизвестное состояние казначейской квитанции");};
        }
    }
    public boolean installed(){return Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null;}
    private ApiServices.Connection api(){return ApiServices.connect("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi",1,"requestDebit","debitStatus","requestRefund","refundStatus","resolveCategory");}
    public Terms freeze(UUID operation,UUID town,UUID actor,long cents,String category,String source,String reason)throws Exception{
        var api=api();if(!api.ready())throw new IllegalStateException("Подтверждаемая городская казна временно недоступна");String canonical=String.valueOf(api.invoke("resolveCategory",new Class<?>[]{String.class},category));return new Terms(operation,town,actor,cents,canonical,source,reason);
    }
    @SuppressWarnings("unchecked")private Map<String,Object> status(ApiServices.Connection api,UUID operation)throws Exception{return(Map<String,Object>)api.invoke("debitStatus",new Class<?>[]{UUID.class},operation);}
    public Map<String,Object> snapshot(Terms terms,UUID operation,boolean refund,String refundReason)throws Exception{var api=api();if(!api.ready())throw new IllegalStateException("Подтверждаемая казна недоступна");var receipt=status(api,operation);terms.result(receipt,operation,refund,refundReason);return Map.copyOf(receipt);}
    public CompletionStage<Result> debit(Terms terms,boolean mayRequest){if(mayRequest)return debit(terms);try{return CompletableFuture.completedFuture(terms.result(snapshot(terms,terms.operation(),false,""),terms.operation(),false,""));}catch(ReceiptMismatch mismatch){return CompletableFuture.completedFuture(Result.REVIEW);}catch(Exception wait){return CompletableFuture.completedFuture(Result.WAIT);}}
    /** Call only after the consumer has ACKed its intent and authorizes this exact request. */
    public CompletionStage<Result> debit(Terms terms){try{
        var api=api();if(!api.ready())return CompletableFuture.completedFuture(Result.WAIT);var receipt=status(api,terms.operation());var observed=terms.result(receipt,terms.operation(),false,"");if(!Objects.equals(receipt.get("status"),"ABSENT"))return CompletableFuture.completedFuture(observed);
        @SuppressWarnings("unchecked")var request=(CompletionStage<Map<String,Object>>)api.invoke("requestDebit",new Class<?>[]{UUID.class,UUID.class,String.class,String.class,long.class,String.class,UUID.class,String.class},terms.operation(),terms.town(),terms.category(),terms.source(),terms.cents(),terms.reason(),terms.actor(),OWNER);
        return request.handle((ack,error)->error==null?terms.result(ack,terms.operation(),false,""):Result.WAIT);
    }catch(ReceiptMismatch mismatch){return CompletableFuture.completedFuture(Result.REVIEW);}catch(Exception wait){return CompletableFuture.completedFuture(Result.WAIT);}}
    /** Return only the exact unused paid reserve, to its original town and budget category. */
    public CompletionStage<Result> refund(Terms terms,UUID operation,String reason){try{
        var api=api();if(!api.ready())return CompletableFuture.completedFuture(Result.WAIT);var receipt=status(api,operation);var observed=terms.result(receipt,operation,true,reason);if(!Objects.equals(receipt.get("status"),"ABSENT"))return CompletableFuture.completedFuture(observed);
        var debit=status(api,terms.operation());var backing=terms.result(debit,terms.operation(),false,"");if(backing!=Result.PAID)return CompletableFuture.completedFuture(backing==Result.WAIT?Result.WAIT:Result.REVIEW);
        @SuppressWarnings("unchecked")var request=(CompletionStage<Map<String,Object>>)api.invoke("requestRefund",new Class<?>[]{UUID.class,UUID.class,UUID.class,String.class,String.class},operation,terms.operation(),terms.actor(),OWNER,reason);
        return request.handle((ack,error)->error==null?terms.result(ack,operation,true,reason):Result.WAIT);
    }catch(ReceiptMismatch mismatch){return CompletableFuture.completedFuture(Result.REVIEW);}catch(Exception wait){return CompletableFuture.completedFuture(Result.WAIT);}}
}
