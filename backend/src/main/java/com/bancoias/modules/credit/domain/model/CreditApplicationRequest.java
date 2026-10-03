package com.bancoias.modules.credit.domain.model;

import com.bancoias.shared.domain.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.Objects;

public record CreditApplicationRequest(
        String applicationReference,
        String customerId,
        BigDecimal amount,
        int termMonths) {

    public static final int MIN_TERM_MONTHS = 6;
    public static final int MAX_TERM_MONTHS = 60;

    public CreditApplicationRequest {
        Objects.requireNonNull(applicationReference, "applicationReference");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new BusinessRuleViolationException("INVALID_AMOUNT", "El monto debe ser mayor a cero.");
        }
        if (termMonths < MIN_TERM_MONTHS || termMonths > MAX_TERM_MONTHS) {
            throw new BusinessRuleViolationException("INVALID_TERM",
                    "El plazo debe estar entre %d y %d meses.".formatted(MIN_TERM_MONTHS, MAX_TERM_MONTHS));
        }
    }

    public boolean hasSameDataAs(CreditApplication application) {
        return customerId.equals(application.customerId())
                && amount.compareTo(application.amount()) == 0
                && termMonths == application.termMonths();
    }
}
