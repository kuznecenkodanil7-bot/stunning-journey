package com.raidminer.helper.storage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Небольшой буфер последних сообщений чата.
 * Нужен как fallback для случаев, где ник не спрятан в ClickEvent/Insertion style.
 */
public final class ChatMessageMemory {
    private static final int MAX_MESSAGES = 80;
    private static final Deque<String> MESSAGES = new ArrayDeque<>();

    private ChatMessageMemory() {}

    public static synchronized void push(String plainMessage) {
        if (plainMessage == null || plainMessage.isBlank()) {
            return;
        }
        MESSAGES.addFirst(plainMessage);
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.removeLast();
        }
    }

    public static synchronized Optional<String> latestPlainMessage() {
        return MESSAGES.isEmpty() ? Optional.empty() : Optional.of(MESSAGES.peekFirst());
    }
}
