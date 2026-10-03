package com.bancoias.modules.credit.application.usecases;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import reactor.core.publisher.Mono;

public interface GetCreditApplicationUseCase {

    Mono<CreditApplication> execute(String applicationReference);
}
