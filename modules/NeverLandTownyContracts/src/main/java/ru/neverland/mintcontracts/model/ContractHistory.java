package ru.neverland.mintcontracts.model;
import java.util.UUID;
public record ContractHistory(UUID contractId,String templateId,long endedAt,int progress,int goal,double paid,double refunded,ContractStatus status,String name){
    public ContractHistory(UUID id,String template,long ended,int progress,int goal,double paid,double refunded,ContractStatus status){this(id,template,ended,progress,goal,paid,refunded,status,null);}
}
