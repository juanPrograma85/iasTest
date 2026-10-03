package com.bancoias.modules.credit.domain.ports;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditApplicationRepositoryPort {

    Mono<CreditApplication> findByApplicationReference(String applicationReference);

    /** Emite {@code DuplicateApplicationReferenceException} si la referencia ya existe. */
    Mono<CreditApplication> save(CreditApplication application);

    Flux<CreditApplication> findRecent(int limit);
}
