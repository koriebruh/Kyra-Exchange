package com.kyra.app.wallet;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.WalletApi;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;

/**
 * User-facing custody endpoints (kyra-doc/modules/08). Authenticated; the owner is
 * the JWT subject. Delegates to {@link WalletApi}, which runs the configured
 * {@code CustodyProvider} (mock in dev/test, web3j self-custody in prod). The
 * admin approval side of withdrawals lives in {@code /v1/admin}.
 */
@Path("/v1/wallet")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class WalletResource {

    private final WalletApi wallet;
    private final JsonWebToken jwt;

    public WalletResource(WalletApi wallet, JsonWebToken jwt) {
        this.wallet = wallet;
        this.jwt = jwt;
    }

    public record DepositAddressResponse(String asset, String address) {
    }

    public record WithdrawRequest(String asset, BigDecimal amount, String toAddress) {
    }

    public record WithdrawResponse(String withdrawId) {
    }

    /** Get (or create) this user's deposit address for an asset. */
    @GET
    @Path("/address")
    public DepositAddressResponse depositAddress(@QueryParam("asset") String asset) {
        AssetId a = AssetId.of(asset);
        return new DepositAddressResponse(asset, wallet.depositAddress(jwt.getSubject(), a));
    }

    /** Request a withdrawal. Returns the withdrawal id; funds are held immediately,
     *  broadcast happens after approval (see /v1/admin). */
    @POST
    @Path("/withdraw")
    public WithdrawResponse withdraw(WithdrawRequest req) {
        AssetId a = AssetId.of(req.asset());
        String withdrawId = wallet.requestWithdrawal(
                jwt.getSubject(), a, Money.of(a, req.amount()), req.toAddress());
        return new WithdrawResponse(withdrawId);
    }
}
