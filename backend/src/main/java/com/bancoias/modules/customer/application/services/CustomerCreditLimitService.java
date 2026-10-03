package com.bancoias.modules.customer.application.services;

import com.bancoias.modules.customer.application.usecases.FindCustomerUseCase;
import com.bancoias.modules.customer.application.usecases.ReserveApprovedAmountUseCase;
import com.bancoias.modules.customer.domain.model.Customer;
import com.bancoias.modules.customer.domain.ports.CustomerRepositoryPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CustomerCreditLimitService implements FindCustomerUseCase, ReserveApprovedAmountUseCase {

    private final CustomerRepositoryPort customers;

    public CustomerCreditLimitService(CustomerRepositoryPort customers) {
        this.customers = customers;
    }

    @Override
    public Mono<Customer> execute(String customerId) {
        return customers.findByCustomerId(customerId);
    }

    @Override
    public Mono<Boolean> execute(String customerId, BigDecimal amount) {
        return customers.tryIncreaseApprovedAmount(customerId, amount);
    }
}
