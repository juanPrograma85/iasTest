package com.bancoias.modules.credit.application.services;

import com.bancoias.modules.credit.application.usecases.GetCreditApplicationUseCase;
import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetCreditApplicationService implements GetCreditApplicationUseCase {

    private final CreditApplicationRepositoryPort applications;

    public GetCreditApplicationService(CreditApplicationRepositoryPort applications) {
        this.applications = applications;
    }

    @Override
    public Mono<CreditApplication> execute(String applicationReference) {
        return applications.findByApplicationReference(applicationReference)
                .switchIfEmpty(Mono.error(() -> new ResourceNotFoundException(
                        "CREDIT_APPLICATION_NOT_FOUND", "No existe una solicitud con la referencia indicada.")));
    }
}
