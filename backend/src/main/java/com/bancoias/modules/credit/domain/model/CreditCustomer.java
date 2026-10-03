package com.bancoias.modules.credit.domain.model;

/** Vista del cliente dentro del contexto Credit; solo lo que la decisión de crédito necesita. */
public record CreditCustomer(String customerId, boolean eligible) {
}
