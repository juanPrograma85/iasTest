package com.bancoias.modules.credit.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.modules.credit.domain.ports.DuplicateApplicationReferenceException;
import com.bancoias.shared.domain.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProcessCreditApplicationServiceTest {

    private static final CreditApplicationRequest REQUEST =
            new CreditApplicationRequest("REF-001", "CLI-1001", new BigDecimal("6000000"), 24);
    private static final CreditApplication APPROVED =
            CreditApplication.approve(REQUEST, Instant.parse("2026-10-03T15:20:00Z"));

    private CreditApplicationRepositoryPort applications;
    private CreditDecisionService decisionService;
    private ProcessCreditApplicationService service;

    @BeforeEach
    void setUp() {
        applications = mock(CreditApplicationRepositoryPort.class);
        decisionService = mock(CreditDecisionService.class);
        service = new ProcessCreditApplicationService(applications, decisionService);
    }

    @Test
    void processesNewReference() {
        when(applications.findByApplicationReference("REF-001")).thenReturn(Mono.empty());
        when(decisionService.decide(REQUEST)).thenReturn(Mono.just(APPROVED));

        StepVerifier.create(service.execute(REQUEST))
                .assertNext(result -> {
                    assertThat(result.application()).isEqualTo(APPROVED);
                    assertThat(result.idempotentReplay()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void replaysIdenticalReferenceWithoutReprocessing() {
        when(applications.findByApplicationReference("REF-001")).thenReturn(Mono.just(APPROVED));

        StepVerifier.create(service.execute(REQUEST))
                .assertNext(result -> {
                    assertThat(result.application()).isEqualTo(APPROVED);
                    assertThat(result.idempotentReplay()).isTrue();
                })
                .verifyComplete();
        verify(decisionService, never()).decide(any());
    }

    @Test
    void rejectsReferenceReusedWithDifferentData() {
        when(applications.findByApplicationReference("REF-001")).thenReturn(Mono.just(APPROVED));
        var different = new CreditApplicationRequest("REF-001", "CLI-1001", new BigDecimal("7000000"), 24);

        StepVerifier.create(service.execute(different))
                .expectError(ConflictException.class)
                .verify();
        verify(decisionService, never()).decide(any());
    }

    @Test
    void resolvesConcurrentDuplicateAsReplay() {
        when(applications.findByApplicationReference("REF-001"))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(APPROVED));
        when(decisionService.decide(REQUEST))
                .thenReturn(Mono.error(new DuplicateApplicationReferenceException("REF-001", null)));

        StepVerifier.create(service.execute(REQUEST))
                .assertNext(result -> assertThat(result.idempotentReplay()).isTrue())
                .verifyComplete();
    }
}
