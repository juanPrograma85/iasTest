package com.bancoias.modules.customer.application.usecases;

import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface ReserveApprovedAmountUseCase {

    /** Emite {@code true} si el cupo fue reservado atómicamente. */
    Mono<Boolean> execute(String customerId, BigDecimal amount);
}
