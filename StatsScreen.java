package com.raidminer.helper.storage;

import com.raidminer.helper.rules.PunishmentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** Данные только текущей сессии клиента. После перезапуска статистика обнуляется. */
public final class SessionData {
    private int warn;
    private int mute;
    private int ban;
    private int ipban;
    private final int recentLimit;
    private final LinkedList<String> recentPlayers = new LinkedList<>();

    public SessionData(int recentLimit) {
        this.recentLimit = Math.max(1, recentLimit);
    }

    public synchronized void increment(PunishmentType type) {
        switch (type) {
            case WARN -> warn++;
            case MUTE -> mute++;
            case BAN -> ban++;
            case IPBAN -> ipban++;
        }
    }

    public synchronized void addRecent(String nick) {
        if (nick == null || nick.isBlank()) {
            return;
        }
        recentPlayers.removeIf(existing -> existing.equalsIgnoreCase(nick));
        recentPlayers.addFirst(nick);
        while (recentPlayers.size() > recentLimit) {
            recentPlayers.removeLast();
        }
    }

    public synchronized List<String> recentPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(recentPlayers));
    }

    public synchronized int warn() { return warn; }
    public synchronized int mute() { return mute; }
    public synchronized int ban() { return ban; }
    public synchronized int ipban() { return ipban; }
}
