package com.bancoias.modules.credit.presentation.http.dto;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationStatus;
import com.bancoias.modules.credit.domain.model.DecisionReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreditApplicationResponse(
        String applicationReference,
        String customerId,
        BigDecimal amount,
        int termMonths,
        CreditApplicationStatus status,
        Instant processedAt,
        DecisionReason decisionReason,
        Boolean idempotentReplay) {

    public static CreditApplicationResponse of(CreditApplication application) {
        return of(application, null);
    }

    public static CreditApplicationResponse of(CreditApplication application, Boolean idempotentReplay) {
        return new CreditApplicationResponse(application.applicationReference(), application.customerId(),
                application.amount(), application.termMonths(), application.status(), application.processedAt(),
                application.decisionReason(), idempotentReplay);
    }
}
