package com.bancoias.modules.credit.presentation.http.dto;

import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreateCreditApplicationRequest(
        @Schema(example = "REF-001")
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$",
                message = "debe contener de 1 a 64 letras, dígitos, '-' o '_'")
        String applicationReference,

        @Schema(example = "CLI-1001")
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^[A-Za-z0-9-]{1,20}$",
                message = "debe contener de 1 a 20 letras, dígitos o '-'")
        String customerId,

        @Schema(example = "6000000")
        @NotNull(message = "es obligatorio")
        BigDecimal amount,

        @Schema(example = "24")
        @NotNull(message = "es obligatorio")
        Integer termMonths) {

    public CreditApplicationRequest toDomain() {
        return new CreditApplicationRequest(applicationReference, customerId, amount, termMonths);
    }
}
