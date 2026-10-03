package com.bancoias.shared.application;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface TransactionRunner {

    <T> Mono<T> inTransaction(Supplier<Mono<T>> work);
}
