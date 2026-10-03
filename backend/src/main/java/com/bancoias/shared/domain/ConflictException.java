package com.bancoias.shared.domain;

public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
