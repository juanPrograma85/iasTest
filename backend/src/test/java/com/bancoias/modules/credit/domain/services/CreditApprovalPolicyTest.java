package com.bancoias.modules.credit.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.model.CreditApplicationStatus;
import com.bancoias.modules.credit.domain.model.CreditCustomer;
import com.bancoias.modules.credit.domain.model.DecisionReason;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CreditApprovalPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-03T15:20:00Z");
    private static final CreditApplicationRequest REQUEST =
            new CreditApplicationRequest("REF-001", "CLI-1001", new BigDecimal("6000000"), 24);
    private static final CreditCustomer ELIGIBLE = new CreditCustomer("CLI-1001", true);
    private static final CreditCustomer BLOCKED = new CreditCustomer("CLI-1002", false);

    private final CreditApprovalPolicy policy = new CreditApprovalPolicy();

    @Test
    void onlyEligibleCustomersRequireLimitReservation() {
        assertThat(policy.requiresCreditLimitReservation(ELIGIBLE)).isTrue();
        assertThat(policy.requiresCreditLimitReservation(BLOCKED)).isFalse();
    }

    @Test
    void approvesEligibleCustomerWithReservedLimit() {
        var application = policy.decide(REQUEST, ELIGIBLE, true, NOW);

        assertThat(application.status()).isEqualTo(CreditApplicationStatus.APPROVED);
        assertThat(application.decisionReason()).isEqualTo(DecisionReason.CREDIT_LIMIT_AVAILABLE);
        assertThat(application.processedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsEligibleCustomerWithoutReservedLimit() {
        var application = policy.decide(REQUEST, ELIGIBLE, false, NOW);

        assertThat(application.status()).isEqualTo(CreditApplicationStatus.REJECTED);
        assertThat(application.decisionReason()).isEqualTo(DecisionReason.CREDIT_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsBlockedCustomerRegardlessOfReservation() {
        assertThat(policy.decide(REQUEST, BLOCKED, false, NOW).decisionReason())
                .isEqualTo(DecisionReason.CUSTOMER_NOT_ELIGIBLE);
        assertThat(policy.decide(REQUEST, BLOCKED, true, NOW).decisionReason())
                .isEqualTo(DecisionReason.CUSTOMER_NOT_ELIGIBLE);
    }
}
