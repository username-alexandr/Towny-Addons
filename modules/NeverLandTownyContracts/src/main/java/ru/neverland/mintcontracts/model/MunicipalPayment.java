package ru.neverland.mintcontracts.model;
import java.util.*;
import java.math.BigDecimal;

public record MunicipalPayment(UUID id,UUID contract,UUID town,UUID account,Kind kind,long cents,Phase phase,long created) {
    public enum Kind { RESERVE, REWARD, REFUND }
    public enum Phase { READY, PENDING, DONE, REJECTED }
    public MunicipalPayment {
        Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(account);Objects.requireNonNull(kind);Objects.requireNonNull(phase);
        if(cents<=0||cents>100_000_000_000L||created<0||kind!=Kind.REWARD&&!town.equals(account)||kind==Kind.RESERVE&&contract==null)throw new IllegalArgumentException("Некорректный платёж контракта");
    }
    public MunicipalPayment phase(Phase value){return new MunicipalPayment(id,contract,town,account,kind,cents,value,created);}
    public static long amount(String text){try{long n=new BigDecimal(text.replace(',','.')).movePointRight(2).longValueExact();if(n<0||n>100_000_000_000L)throw new IllegalArgumentException();return n;}catch(RuntimeException ex){throw new IllegalArgumentException("Сумма: от 0 до 1 000 000 000 монет, максимум два знака после запятой");}}
    public static UUID key(UUID contract,String recipient){return UUID.nameUUIDFromBytes(("municipal:"+contract+":"+recipient).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
