package com.bancoias.modules.credit.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditApplication(
        String applicationReference,
        String customerId,
        BigDecimal amount,
        int termMonths,
        CreditApplicationStatus status,
        DecisionReason decisionReason,
        Instant processedAt) {

    public static CreditApplication approve(CreditApplicationRequest request, Instant processedAt) {
        return from(request, CreditApplicationStatus.APPROVED, DecisionReason.CREDIT_LIMIT_AVAILABLE, processedAt);
    }

    public static CreditApplication reject(CreditApplicationRequest request, DecisionReason reason, Instant processedAt) {
        if (reason == DecisionReason.CREDIT_LIMIT_AVAILABLE) {
            throw new IllegalArgumentException("A rejected application requires a rejection reason");
        }
        return from(request, CreditApplicationStatus.REJECTED, reason, processedAt);
    }

    private static CreditApplication from(CreditApplicationRequest request, CreditApplicationStatus status,
                                          DecisionReason reason, Instant processedAt) {
        return new CreditApplication(request.applicationReference(), request.customerId(), request.amount(),
                request.termMonths(), status, reason, processedAt);
    }
}
