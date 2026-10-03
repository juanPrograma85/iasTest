package com.bancoias.shared.domain;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
