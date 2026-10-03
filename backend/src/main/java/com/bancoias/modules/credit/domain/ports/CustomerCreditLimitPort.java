package com.bancoias.modules.credit.domain.ports;

import com.bancoias.modules.credit.domain.model.CreditCustomer;
import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface CustomerCreditLimitPort {

    Mono<CreditCustomer> findByCustomerId(String customerId);

    /** Reserva atómicamente el cupo; emite {@code false} si no es posible sin superar el máximo. */
    Mono<Boolean> tryReserveCreditLimit(String customerId, BigDecimal amount);
}
