package com.bancoias.modules.customer.domain.ports;

import com.bancoias.modules.customer.domain.model.Customer;
import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface CustomerRepositoryPort {

    Mono<Customer> findByCustomerId(String customerId);

    /**
     * Incrementa atómicamente el cupo aprobado solo si el cliente es ELIGIBLE y no supera su máximo.
     * Emite {@code true} si el cupo fue reservado.
     */
    Mono<Boolean> tryIncreaseApprovedAmount(String customerId, BigDecimal amount);
}
