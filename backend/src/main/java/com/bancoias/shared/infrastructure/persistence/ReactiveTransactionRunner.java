package com.bancoias.shared.infrastructure.persistence;

import com.bancoias.shared.application.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTransactionRunner implements TransactionRunner {

    private final TransactionalOperator transactionalOperator;

    public ReactiveTransactionRunner(ReactiveTransactionManager transactionManager) {
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public <T> Mono<T> inTransaction(Supplier<Mono<T>> work) {
        return transactionalOperator.transactional(Mono.defer(work));
    }
}
