package com.kyra.app.stream;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks which channels each WebSocket connection is subscribed to
 * (kyra-doc/modules/07, F5). Connection ids come from websockets-next.
 */
@ApplicationScoped
public class WsSubscriptions {

    private final ConcurrentHashMap<String, Set<String>> byConnection = new ConcurrentHashMap<>();

    public void register(String connectionId) {
        byConnection.computeIfAbsent(connectionId, k -> new CopyOnWriteArraySet<>());
    }

    public void subscribe(String connectionId, String channel) {
        byConnection.computeIfAbsent(connectionId, k -> new CopyOnWriteArraySet<>()).add(channel);
    }

    public void unsubscribe(String connectionId, String channel) {
        Set<String> channels = byConnection.get(connectionId);
        if (channels != null) {
            channels.remove(channel);
        }
    }

    public boolean isSubscribed(String connectionId, String channel) {
        Set<String> channels = byConnection.get(connectionId);
        return channels != null && channels.contains(channel);
    }

    public void remove(String connectionId) {
        byConnection.remove(connectionId);
    }
}
