package com.raidminer.helper.rules;

public enum PunishmentType {
    WARN("warn", "warn"),
    MUTE("mute", "mute"),
    BAN("ban", "ban"),
    IPBAN("ipban", "ipban");

    private final String commandName;
    private final String folderName;

    PunishmentType(String commandName, String folderName) {
        this.commandName = commandName;
        this.folderName = folderName;
    }

    public String commandName() { return commandName; }
    public String folderName() { return folderName; }
}
