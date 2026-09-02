package com.palmergames.bukkit.towny;

import org.bukkit.command.CommandExecutor;

public class TownyCommandAddonAPI {
    public enum CommandType { TOWN }
    public static boolean addSubCommand(CommandType type, String name, CommandExecutor executor) { return true; }
    public static boolean removeSubCommand(CommandType type, String name) { return true; }
}
