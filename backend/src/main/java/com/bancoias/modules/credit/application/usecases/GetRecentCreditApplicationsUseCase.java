package com.bancoias.modules.credit.application.usecases;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import reactor.core.publisher.Flux;

public interface GetRecentCreditApplicationsUseCase {

    Flux<CreditApplication> execute(int limit);
}
