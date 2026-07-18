package com.kyra.app.stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Public market-data stream (kyra-doc/modules/07, F5). Clients subscribe to
 * channels such as {@code trades:BTC-USDT}; the {@link StreamBroadcaster} pushes
 * events to subscribers. Read-only and public — no user-identifying data.
 *
 * <p>Subscribe: {@code {"action":"subscribe","channels":["trades:BTC-USDT"]}}.
 */
@WebSocket(path = "/v1/stream")
public class MarketStreamSocket {

    public record Command(String action, List<String> channels) {
    }

    public record Ack(String result, List<String> channels) {
    }

    @Inject
    WebSocketConnection connection;

    @Inject
    WsSubscriptions subscriptions;

    @Inject
    ObjectMapper mapper;

    @OnOpen
    public void onOpen() {
        subscriptions.register(connection.id());
    }

    @OnTextMessage
    public String onMessage(String raw) throws Exception {
        Command cmd = mapper.readValue(raw, Command.class);
        List<String> channels = cmd.channels() == null ? List.of() : cmd.channels();
        String action = cmd.action() == null ? "" : cmd.action();
        switch (action) {
            case "subscribe" -> channels.forEach(c -> subscriptions.subscribe(connection.id(), c));
            case "unsubscribe" -> channels.forEach(c -> subscriptions.unsubscribe(connection.id(), c));
            default -> {
                return mapper.writeValueAsString(new Ack("error:unknown_action", channels));
            }
        }
        return mapper.writeValueAsString(new Ack(action + "d", channels));
    }

    @OnClose
    public void onClose() {
        subscriptions.remove(connection.id());
    }
}
