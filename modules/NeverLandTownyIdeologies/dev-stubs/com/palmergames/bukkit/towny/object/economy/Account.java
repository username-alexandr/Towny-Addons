package com.palmergames.bukkit.towny.object.economy;

public abstract class Account {
    public boolean canPayFromHoldings(double amount) { return false; }
    public boolean withdraw(double amount, String reason) { return false; }
}
