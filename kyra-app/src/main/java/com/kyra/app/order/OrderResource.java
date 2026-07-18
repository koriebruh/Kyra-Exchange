package com.kyra.app.order;

import com.kyra.common.money.PairSymbol;
import com.kyra.matching.api.OrderSide;
import com.kyra.matching.api.TimeInForce;
import com.kyra.order.api.OrderApi;
import com.kyra.order.api.OrderView;
import com.kyra.order.api.PlaceOrder;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trading endpoints (kyra-doc/modules/04). All authenticated; the order owner is
 * the JWT subject.
 */
@Path("/v1/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class OrderResource {

    private final OrderApi orders;
    private final JsonWebToken jwt;

    public OrderResource(OrderApi orders, JsonWebToken jwt) {
        this.orders = orders;
        this.jwt = jwt;
    }

    public record PlaceOrderRequest(
            String pair, String side, String tif, BigDecimal price, BigDecimal qty, String clientOrderId) {
    }

    @POST
    public OrderView place(PlaceOrderRequest req) {
        PlaceOrder cmd = new PlaceOrder(
                jwt.getSubject(),
                PairSymbol.parse(req.pair()),
                OrderSide.valueOf(req.side().toUpperCase()),
                req.tif() == null ? TimeInForce.GTC : TimeInForce.valueOf(req.tif().toUpperCase()),
                req.price(),
                req.qty(),
                req.clientOrderId());
        return orders.place(cmd);
    }

    @DELETE
    @Path("/{orderId}")
    public Response cancel(@PathParam("orderId") String orderId) {
        orders.cancel(jwt.getSubject(), orderId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{orderId}")
    public OrderView get(@PathParam("orderId") String orderId) {
        return orders.get(jwt.getSubject(), orderId)
                .orElseThrow(() -> new NotFoundException("unknown order: " + orderId));
    }

    @GET
    @Path("/open")
    public List<OrderView> open(@QueryParam("pair") String pair) {
        return orders.openOrders(jwt.getSubject(), PairSymbol.parse(pair));
    }
}
