package com.bancoias.modules.credit.domain.ports;

/** La referencia ya fue persistida por otra transacción concurrente. */
public class DuplicateApplicationReferenceException extends RuntimeException {

    public DuplicateApplicationReferenceException(String applicationReference, Throwable cause) {
        super("Application reference already exists: " + applicationReference, cause);
    }
}
