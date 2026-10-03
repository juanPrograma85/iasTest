package com.bancoias.modules.credit.application.usecases;

import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import reactor.core.publisher.Mono;

public interface ProcessCreditApplicationUseCase {

    Mono<ProcessingResult> execute(CreditApplicationRequest request);
}
