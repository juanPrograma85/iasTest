package com.bancoias.modules.customer.presentation.http.dto;

import com.bancoias.modules.customer.domain.model.Customer;
import com.bancoias.modules.customer.domain.model.CustomerStatus;

public record CustomerResponse(String customerId, CustomerStatus status) {

    public static CustomerResponse of(Customer customer) {
        return new CustomerResponse(customer.customerId(), customer.status());
    }
}
