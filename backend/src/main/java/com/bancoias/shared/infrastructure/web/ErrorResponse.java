package com.bancoias.shared.infrastructure.web;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {
}
