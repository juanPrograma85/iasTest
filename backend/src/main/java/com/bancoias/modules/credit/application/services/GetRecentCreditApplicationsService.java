package com.bancoias.modules.credit.application.services;

import com.bancoias.modules.credit.application.usecases.GetRecentCreditApplicationsUseCase;
import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GetRecentCreditApplicationsService implements GetRecentCreditApplicationsUseCase {

    private final CreditApplicationRepositoryPort applications;

    public GetRecentCreditApplicationsService(CreditApplicationRepositoryPort applications) {
        this.applications = applications;
    }

    @Override
    public Flux<CreditApplication> execute(int limit) {
        return applications.findRecent(limit);
    }
}
