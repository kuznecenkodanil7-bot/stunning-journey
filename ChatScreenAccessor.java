package com.raidminer.helper.rules;

import java.util.List;

public record PunishmentRule(
        PunishmentType type,
        String code,
        String title,
        List<String> durations,
        String fixedDuration,
        String commandReason
) {
    public boolean hasDuration() {
        return fixedDuration != null && !fixedDuration.isBlank() || durations != null && !durations.isEmpty();
    }

    public boolean isFixedDuration() {
        return fixedDuration != null && !fixedDuration.isBlank();
    }

    public boolean noDurationToken() {
        return !hasDuration();
    }

    public String display() {
        return code + " — " + title;
    }
}
