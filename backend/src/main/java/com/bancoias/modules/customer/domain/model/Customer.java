package com.bancoias.modules.customer.domain.model;

import java.math.BigDecimal;

public record Customer(
        String customerId,
        CustomerStatus status,
        BigDecimal maxApprovedAmount,
        BigDecimal currentApprovedAmount) {

    public boolean isEligible() {
        return status == CustomerStatus.ELIGIBLE;
    }
}
