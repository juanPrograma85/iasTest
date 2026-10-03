package com.bancoias.modules.credit.application.services;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.model.CreditCustomer;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.modules.credit.domain.ports.CustomerCreditLimitPort;
import com.bancoias.modules.credit.domain.services.CreditApprovalPolicy;
import com.bancoias.shared.application.TransactionRunner;
import com.bancoias.shared.domain.BusinessRuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CreditDecisionService {

    private final CustomerCreditLimitPort customers;
    private final CreditApplicationRepositoryPort applications;
    private final CreditApprovalPolicy policy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CreditDecisionService(CustomerCreditLimitPort customers,
                                 CreditApplicationRepositoryPort applications,
                                 CreditApprovalPolicy policy,
                                 TransactionRunner transactions,
                                 Clock clock) {
        this.customers = customers;
        this.applications = applications;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Reserva de cupo e inserción de la solicitud en una única transacción. */
    public Mono<CreditApplication> decide(CreditApplicationRequest request) {
        return transactions.inTransaction(() -> customers.findByCustomerId(request.customerId())
                .switchIfEmpty(Mono.error(() -> new BusinessRuleViolationException(
                        "CUSTOMER_NOT_FOUND", "El cliente no existe.")))
                .flatMap(customer -> reserveIfRequired(request, customer)
                        .map(reserved -> policy.decide(request, customer, reserved, now())))
                .flatMap(applications::save));
    }

    private Mono<Boolean> reserveIfRequired(CreditApplicationRequest request, CreditCustomer customer) {
        return policy.requiresCreditLimitReservation(customer)
                ? customers.tryReserveCreditLimit(request.customerId(), request.amount())
                : Mono.just(false);
    }

    // PostgreSQL almacena microsegundos; truncar mantiene igualdad entre respuesta y lectura posterior.
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
