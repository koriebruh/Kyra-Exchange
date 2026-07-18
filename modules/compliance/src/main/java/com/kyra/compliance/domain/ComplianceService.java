package com.kyra.compliance.domain;

import com.kyra.common.id.Ids;
import com.kyra.common.money.AssetId;
import com.kyra.compliance.api.AddressScreener;
import com.kyra.compliance.api.ComplianceApi;
import com.kyra.compliance.api.KycLevel;
import com.kyra.compliance.api.KycProvider;
import com.kyra.compliance.api.ScreeningResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * KYC standing and AML screening (kyra-doc/modules/10). Verification and
 * screening are delegated to pluggable providers; this service records the
 * user's level and screening decisions. Raising a level is only ever the result
 * of a provider approval.
 */
@ApplicationScoped
public class ComplianceService implements ComplianceApi {

    private static final Logger LOG = Logger.getLogger(ComplianceService.class);

    private final EntityManager em;
    private final KycProvider kycProvider;
    private final AddressScreener screener;

    public ComplianceService(EntityManager em, KycProvider kycProvider, AddressScreener screener) {
        this.em = em;
        this.kycProvider = kycProvider;
        this.screener = screener;
    }

    @Override
    @Transactional
    public KycLevel kycLevel(String userId) {
        KycProfileEntity p = em.find(KycProfileEntity.class, userId);
        return p == null ? KycLevel.L0 : KycLevel.valueOf(p.level);
    }

    @Override
    @Transactional
    public KycProvider.Outcome submitKyc(String userId, KycLevel requestedLevel) {
        String submissionId = Ids.newUlid();
        KycProvider.Outcome outcome = kycProvider.verify(submissionId, userId, requestedLevel);

        recordSubmission(submissionId, userId, requestedLevel, outcome);

        if (outcome == KycProvider.Outcome.APPROVED) {
            KycProfileEntity p = em.find(KycProfileEntity.class, userId);
            boolean isNew = p == null;
            if (isNew) {
                p = new KycProfileEntity();
                p.userId = userId;
                p.level = requestedLevel.name();
            } else if (requestedLevel.atLeast(KycLevel.valueOf(p.level))) {
                // never lower an existing level on a same/lower approval
                p.level = requestedLevel.name();
            }
            p.status = "APPROVED";
            p.updatedAt = Instant.now();
            if (isNew) {
                em.persist(p);
            }
            LOG.infof("kyc approved: user=%s level=%s", userId, p.level);
        } else {
            LOG.infof("kyc %s: user=%s requested=%s", outcome, userId, requestedLevel);
        }
        return outcome;
    }

    @Override
    public ScreeningResult screenAddress(String address, AssetId asset) {
        ScreeningResult result = screener.screen(address, asset);
        if (result != ScreeningResult.CLEAR) {
            LOG.warnf("address screening %s for asset %s", result, asset);
        }
        return result;
    }

    @Override
    @Transactional
    public void freezeAccount(String userId, String reason) {
        if (em.find(AccountFreezeEntity.class, userId) != null) {
            return; // already frozen
        }
        AccountFreezeEntity f = new AccountFreezeEntity();
        f.userId = userId;
        f.reason = reason == null ? "" : reason;
        f.frozenAt = Instant.now();
        em.persist(f);
        LOG.warnf("account frozen: user=%s reason=%s", userId, reason);
    }

    @Override
    @Transactional
    public void unfreezeAccount(String userId) {
        AccountFreezeEntity f = em.find(AccountFreezeEntity.class, userId);
        if (f != null) {
            em.remove(f);
            LOG.infof("account unfrozen: user=%s", userId);
        }
    }

    @Override
    @Transactional
    public boolean isFrozen(String userId) {
        return em.find(AccountFreezeEntity.class, userId) != null;
    }

    private void recordSubmission(String id, String userId, KycLevel level, KycProvider.Outcome outcome) {
        KycSubmissionEntity s = new KycSubmissionEntity();
        s.id = id;
        s.userId = userId;
        s.level = level.name();
        s.outcome = outcome.name();
        s.submittedAt = Instant.now();
        em.persist(s);
    }
}
