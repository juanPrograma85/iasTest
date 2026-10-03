package com.bancoias.modules.credit.domain.services;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.model.CreditCustomer;
import com.bancoias.modules.credit.domain.model.DecisionReason;
import java.time.Instant;

/** Reglas de aprobación. La verificación de cupo la resuelve atómicamente la reserva en persistencia. */
public class CreditApprovalPolicy {

    public boolean requiresCreditLimitReservation(CreditCustomer customer) {
        return customer.eligible();
    }

    public CreditApplication decide(CreditApplicationRequest request, CreditCustomer customer,
                                    boolean creditLimitReserved, Instant processedAt) {
        if (!customer.eligible()) {
            return CreditApplication.reject(request, DecisionReason.CUSTOMER_NOT_ELIGIBLE, processedAt);
        }
        if (!creditLimitReserved) {
            return CreditApplication.reject(request, DecisionReason.CREDIT_LIMIT_EXCEEDED, processedAt);
        }
        return CreditApplication.approve(request, processedAt);
    }
}
