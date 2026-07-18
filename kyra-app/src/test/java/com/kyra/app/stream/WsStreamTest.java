package com.kyra.app.stream;

import com.kyra.account.api.AccountApi;
import com.kyra.account.api.AccountKey;
import com.kyra.account.api.EntryLine;
import com.kyra.account.api.JournalRequest;
import com.kyra.account.api.JournalType;
import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.common.money.PairSymbol;
import com.kyra.market.api.Asset;
import com.kyra.market.api.AssetStatus;
import com.kyra.market.api.MarketApi;
import com.kyra.market.api.Pair;
import com.kyra.market.api.PairStatus;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.PlaceOrder;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WsStreamTest {

    @TestHTTPResource("/v1/stream")
    URI streamUri;

    @Inject
    MarketApi market;

    @Inject
    AccountApi ledger;

    @Inject
    OrderApi orders;

    @Test
    void subscribedClientReceivesTradeMessages() throws Exception {
        AssetId base = AssetId.of("WSX");
        AssetId quote = AssetId.of("WSQ");
        PairSymbol pair = new PairSymbol(base, quote);
        market.registerAsset(new Asset(base, "WS Base", 8, AssetStatus.ACTIVE, 3));
        market.registerAsset(new Asset(quote, "WS Quote", 6, AssetStatus.ACTIVE, 6));
        market.registerPair(new Pair(pair, bd("1"), bd("0.001"), bd("10"),
                bd("0.001"), bd("10000"), 500, PairStatus.ACTIVE));

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        URI wsUri = URI.create(streamUri.toString().replaceFirst("^http", "ws"));
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(wsUri, new CollectingListener(messages))
                .get(5, TimeUnit.SECONDS);
        try {
            ws.sendText("{\"action\":\"subscribe\",\"channels\":[\"trades:WSX-WSQ\"]}", true).get(2, TimeUnit.SECONDS);

            String ack = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(ack, "should receive a subscribe ack");
            assertTrue(ack.contains("subscribed"), "ack: " + ack);

            // now cause a trade
            String seller = fund(base, "1");
            String buyer = fund(quote, "100000");
            orders.place(new PlaceOrder(seller, pair, OrderSide.SELL, TimeInForce.GTC, bd("50000"), bd("1"), Ids.newUlid()));
            orders.place(new PlaceOrder(buyer, pair, OrderSide.BUY, TimeInForce.GTC, bd("50000"), bd("1"), Ids.newUlid()));

            String tradeMsg = pollFor(messages, "trades:WSX-WSQ", 5);
            assertNotNull(tradeMsg, "should receive a trade stream message");
            assertTrue(tradeMsg.contains("\"price\":50000"), "trade msg: " + tradeMsg);
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    private static String pollFor(BlockingQueue<String> q, String needle, int seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String msg = q.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (msg == null) {
                return null;
            }
            if (msg.contains(needle)) {
                return msg;
            }
        }
        return null;
    }

    private String fund(AssetId asset, String amount) {
        String user = Ids.newUlid();
        Money m = Money.of(asset, bd(amount));
        ledger.post(new JournalRequest(JournalType.DEPOSIT, Ids.newUlid(), List.of(
                EntryLine.of(AccountKey.external(asset), m.negated()),
                EntryLine.of(AccountKey.userMain(user, asset), m))));
        return user;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** Accumulates complete text messages into a queue. */
    private static final class CollectingListener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final StringBuilder buffer = new StringBuilder();

        CollectingListener(BlockingQueue<String> messages) {
            this.messages = messages;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}
