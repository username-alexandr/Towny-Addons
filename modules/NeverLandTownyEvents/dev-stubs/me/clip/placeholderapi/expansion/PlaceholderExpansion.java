package me.clip.placeholderapi.expansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlaceholderExpansion {
    public abstract @NotNull String getIdentifier();
    public abstract @NotNull String getAuthor();
    public abstract @NotNull String getVersion();
    public boolean persist() { return false; }
    public boolean register() { return true; }
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) { return null; }
}
