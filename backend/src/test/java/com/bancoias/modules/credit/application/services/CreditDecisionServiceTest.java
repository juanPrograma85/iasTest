package com.bancoias.modules.credit.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.model.CreditApplicationStatus;
import com.bancoias.modules.credit.domain.model.CreditCustomer;
import com.bancoias.modules.credit.domain.model.DecisionReason;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.modules.credit.domain.ports.CustomerCreditLimitPort;
import com.bancoias.modules.credit.domain.services.CreditApprovalPolicy;
import com.bancoias.shared.application.TransactionRunner;
import com.bancoias.shared.domain.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CreditDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T15:20:00Z");
    private static final BigDecimal SIX_MILLION = new BigDecimal("6000000");

    private CustomerCreditLimitPort customers;
    private CreditApplicationRepositoryPort applications;
    private TransactionRunner transactions;
    private CreditDecisionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        customers = mock(CustomerCreditLimitPort.class);
        applications = mock(CreditApplicationRepositoryPort.class);
        transactions = mock(TransactionRunner.class);
        when(transactions.inTransaction(any())).thenAnswer(inv -> ((Supplier<Mono<?>>) inv.getArgument(0)).get());
        when(applications.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        service = new CreditDecisionService(customers, applications, new CreditApprovalPolicy(), transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approvesWhenLimitIsReserved() {
        givenCustomer("CLI-1001", true);
        when(customers.tryReserveCreditLimit("CLI-1001", SIX_MILLION)).thenReturn(Mono.just(true));

        StepVerifier.create(service.decide(request("CLI-1001")))
                .assertNext(app -> {
                    assertDecision(app, CreditApplicationStatus.APPROVED, DecisionReason.CREDIT_LIMIT_AVAILABLE);
                    assertThat(app.processedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
        verify(transactions).inTransaction(any());
    }

    @Test
    void rejectsWhenLimitIsInsufficient() {
        givenCustomer("CLI-1001", true);
        when(customers.tryReserveCreditLimit("CLI-1001", SIX_MILLION)).thenReturn(Mono.just(false));

        StepVerifier.create(service.decide(request("CLI-1001")))
                .assertNext(app -> assertDecision(app, CreditApplicationStatus.REJECTED, DecisionReason.CREDIT_LIMIT_EXCEEDED))
                .verifyComplete();
        verify(applications).save(any());
    }

    @Test
    void rejectsBlockedCustomerWithoutTouchingLimit() {
        givenCustomer("CLI-1002", false);

        StepVerifier.create(service.decide(request("CLI-1002")))
                .assertNext(app -> assertDecision(app, CreditApplicationStatus.REJECTED, DecisionReason.CUSTOMER_NOT_ELIGIBLE))
                .verifyComplete();
        verify(customers, never()).tryReserveCreditLimit(anyString(), any());
    }

    @Test
    void failsWhenCustomerDoesNotExist() {
        when(customers.findByCustomerId("CLI-9999")).thenReturn(Mono.empty());

        StepVerifier.create(service.decide(request("CLI-9999")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessRuleViolationException.class)
                        .extracting("code").isEqualTo("CUSTOMER_NOT_FOUND"))
                .verify();
        verify(applications, never()).save(any());
    }

    private void givenCustomer(String customerId, boolean eligible) {
        when(customers.findByCustomerId(customerId)).thenReturn(Mono.just(new CreditCustomer(customerId, eligible)));
    }

    private static CreditApplicationRequest request(String customerId) {
        return new CreditApplicationRequest("REF-001", customerId, SIX_MILLION, 24);
    }

    private static void assertDecision(CreditApplication app, CreditApplicationStatus status, DecisionReason reason) {
        assertThat(app.status()).isEqualTo(status);
        assertThat(app.decisionReason()).isEqualTo(reason);
    }
}
