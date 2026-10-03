package com.bancoias.modules.customer.application.usecases;

import com.bancoias.modules.customer.domain.model.Customer;
import reactor.core.publisher.Mono;

public interface FindCustomerUseCase {

    Mono<Customer> execute(String customerId);
}
