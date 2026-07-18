package com.kyra.account.domain;

import com.kyra.account.api.ProofOfReservesApi;
import com.kyra.account.api.ReservesSnapshot;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Proof of reserves (kyra-doc/modules/16 §7). Aggregates every user's balance
 * (main + hold) for an asset from the ledger, commits to them in a Merkle tree,
 * and reports total liabilities. Deterministic: the same balances always yield
 * the same root, so a published root can be independently recomputed.
 */
@ApplicationScoped
public class ProofOfReservesService implements ProofOfReservesApi {

    private final EntityManager em;

    public ProofOfReservesService(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public ReservesSnapshot snapshot(AssetId asset) {
        Map<String, BigDecimal> perUser = balancesByUser(asset);
        List<String> leaves = leaves(asset, perUser);
        BigDecimal total = perUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReservesSnapshot(asset, Money.of(asset, total), merkleRoot(leaves), leaves.size());
    }

    @Override
    @Transactional
    public boolean verifyInclusion(String userId, AssetId asset, BigDecimal balance, String merkleRoot) {
        Map<String, BigDecimal> perUser = balancesByUser(asset);
        List<String> leaves = leaves(asset, perUser);
        if (!merkleRoot(leaves).equals(merkleRoot)) {
            return false; // root is stale/wrong
        }
        return leaves.contains(leaf(userId, asset, balance));
    }

    /** Sum of main + hold per user for the asset (excludes external/kyra accounts). */
    private Map<String, BigDecimal> balancesByUser(AssetId asset) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "select account_key, amount from account.balances "
                                + "where asset = :a and account_key like 'user:%'")
                .setParameter("a", asset.symbol())
                .getResultList();
        Map<String, BigDecimal> perUser = new TreeMap<>();
        for (Object[] r : rows) {
            String key = (String) r[0]; // user:<id>:<asset>:<type>
            String userId = key.split(":")[1];
            perUser.merge(userId, (BigDecimal) r[1], BigDecimal::add);
        }
        return perUser;
    }

    private static List<String> leaves(AssetId asset, Map<String, BigDecimal> perUser) {
        List<String> leaves = new ArrayList<>(perUser.size());
        // TreeMap keeps users sorted -> deterministic leaf order
        for (var e : perUser.entrySet()) {
            leaves.add(leaf(e.getKey(), asset, e.getValue()));
        }
        return leaves;
    }

    private static String leaf(String userId, AssetId asset, BigDecimal balance) {
        return sha256(userId + ":" + asset + ":" + balance.stripTrailingZeros().toPlainString());
    }

    /** Standard binary Merkle root; an odd node is paired with itself. Empty tree = "" root. */
    static String merkleRoot(List<String> leafHashes) {
        if (leafHashes.isEmpty()) {
            return "";
        }
        List<String> level = new ArrayList<>(leafHashes);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = (i + 1 < level.size()) ? level.get(i + 1) : left;
                next.add(sha256(left + right));
            }
            level = next;
        }
        return level.get(0);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
