package com.kyra.app.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyra.settlement.api.TradeSettled;

import io.quarkus.websockets.next.OpenConnections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Pushes settled trades to subscribed WebSocket clients (kyra-doc/modules/07, F5).
 * Sends are fire-and-forget (non-blocking) so a slow consumer never stalls the
 * settlement transaction that produced the trade. The public payload carries no
 * user identity.
 */
@ApplicationScoped
public class StreamBroadcaster {

    private static final Logger LOG = Logger.getLogger(StreamBroadcaster.class);

    public record TradeMessage(String channel, String pair, BigDecimal price, BigDecimal qty, long ts) {
    }

    @Inject
    OpenConnections connections;

    @Inject
    WsSubscriptions subscriptions;

    @Inject
    ObjectMapper mapper;

    void onTradeSettled(@Observes TradeSettled t) {
        String channel = "trades:" + t.pair();
        BigDecimal price = t.quoteAmount().amount().divide(t.baseQty().amount(), 18, RoundingMode.HALF_UP);
        String json;
        try {
            json = mapper.writeValueAsString(new TradeMessage(
                    channel, t.pair().toString(), price, t.baseQty().amount(), Instant.now().toEpochMilli()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.error("failed to serialize trade stream message", e);
            return;
        }
        for (var conn : connections.listAll()) {
            if (subscriptions.isSubscribed(conn.id(), channel)) {
                // fire-and-forget: do not block settlement on a slow client
                conn.sendText(json).subscribe().with(ok -> {
                }, err -> LOG.debugf("ws send failed to %s: %s", conn.id(), err.getMessage()));
            }
        }
    }
}
