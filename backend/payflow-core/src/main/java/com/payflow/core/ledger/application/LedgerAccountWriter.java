package com.payflow.core.ledger.application;

import com.payflow.core.ledger.domain.LedgerAccount;
import com.payflow.core.ledger.domain.LedgerAccountCode;
import com.payflow.core.ledger.persistence.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A separate bean (not a private method on LedgerServiceImpl) so the
 * propagation below actually takes effect - Spring's proxy-based AOP does
 * not intercept self-invocation. REQUIRES_NEW (not NESTED): Spring's
 * JpaTransactionManager does not support real savepoints for JPA
 * ("JpaDialect does not support savepoints"), so NESTED throws at runtime.
 * REQUIRES_NEW uses a genuinely separate transaction/connection instead - if
 * this insert loses the race, only its own transaction rolls back, and the
 * account row it commits independently is harmless: it's idempotent
 * chart-of-accounts metadata, not part of this capture's financial data.
 */
@Component
@RequiredArgsConstructor
class LedgerAccountWriter {

    private final LedgerAccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LedgerAccount create(UUID organizationId, LedgerAccountCode code) {
        return accountRepository.saveAndFlush(new LedgerAccount(organizationId, code));
    }
}
