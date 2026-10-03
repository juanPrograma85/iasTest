package com.bancoias.modules.credit.application.services;

import com.bancoias.modules.credit.application.usecases.ProcessCreditApplicationUseCase;
import com.bancoias.modules.credit.application.usecases.ProcessingResult;
import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.modules.credit.domain.ports.DuplicateApplicationReferenceException;
import com.bancoias.shared.domain.ConflictException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProcessCreditApplicationService implements ProcessCreditApplicationUseCase {

    private final CreditApplicationRepositoryPort applications;
    private final CreditDecisionService decisionService;

    public ProcessCreditApplicationService(CreditApplicationRepositoryPort applications,
                                           CreditDecisionService decisionService) {
        this.applications = applications;
        this.decisionService = decisionService;
    }

    @Override
    public Mono<ProcessingResult> execute(CreditApplicationRequest request) {
        return findExisting(request)
                .switchIfEmpty(Mono.defer(() -> decisionService.decide(request)
                        .map(application -> new ProcessingResult(application, false))
                        // Carrera con la misma referencia: la transacción perdedora ya hizo rollback del cupo.
                        .onErrorResume(DuplicateApplicationReferenceException.class,
                                e -> findExisting(request).switchIfEmpty(Mono.error(e)))));
    }

    private Mono<ProcessingResult> findExisting(CreditApplicationRequest request) {
        return applications.findByApplicationReference(request.applicationReference())
                .flatMap(existing -> replayOrConflict(request, existing));
    }

    private static Mono<ProcessingResult> replayOrConflict(CreditApplicationRequest request, CreditApplication existing) {
        if (request.hasSameDataAs(existing)) {
            return Mono.just(new ProcessingResult(existing, true));
        }
        return Mono.error(new ConflictException("APPLICATION_REFERENCE_CONFLICT",
                "La referencia ya fue procesada con datos diferentes y no puede reutilizarse."));
    }
}
