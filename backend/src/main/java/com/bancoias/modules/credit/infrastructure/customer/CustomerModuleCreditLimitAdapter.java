package com.bancoias.modules.credit.infrastructure.customer;

import com.bancoias.modules.credit.domain.model.CreditCustomer;
import com.bancoias.modules.credit.domain.ports.CustomerCreditLimitPort;
import com.bancoias.modules.customer.application.usecases.FindCustomerUseCase;
import com.bancoias.modules.customer.application.usecases.ReserveApprovedAmountUseCase;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Anti-corruption layer: traduce la API pública del módulo Customer al modelo de Credit. */
@Component
public class CustomerModuleCreditLimitAdapter implements CustomerCreditLimitPort {

    private final FindCustomerUseCase findCustomer;
    private final ReserveApprovedAmountUseCase reserveApprovedAmount;

    public CustomerModuleCreditLimitAdapter(FindCustomerUseCase findCustomer,
                                            ReserveApprovedAmountUseCase reserveApprovedAmount) {
        this.findCustomer = findCustomer;
        this.reserveApprovedAmount = reserveApprovedAmount;
    }

    @Override
    public Mono<CreditCustomer> findByCustomerId(String customerId) {
        return findCustomer.execute(customerId)
                .map(customer -> new CreditCustomer(customer.customerId(), customer.isEligible()));
    }

    @Override
    public Mono<Boolean> tryReserveCreditLimit(String customerId, BigDecimal amount) {
        return reserveApprovedAmount.execute(customerId, amount);
    }
}
