package com.bancoias.modules.customer.presentation.http.controllers;

import com.bancoias.modules.customer.application.usecases.FindCustomerUseCase;
import com.bancoias.modules.customer.presentation.http.dto.CustomerResponse;
import com.bancoias.shared.domain.ResourceNotFoundException;
import com.bancoias.shared.infrastructure.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final FindCustomerUseCase findCustomer;

    public CustomerController(FindCustomerUseCase findCustomer) {
        this.findCustomer = findCustomer;
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Verifica la existencia y el estado de un cliente (sin exponer montos)")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Mono<CustomerResponse> getByCustomerId(@PathVariable String customerId) {
        return findCustomer.execute(customerId)
                .map(CustomerResponse::of)
                .switchIfEmpty(Mono.error(() -> new ResourceNotFoundException(
                        "CUSTOMER_NOT_FOUND", "El cliente no existe.")));
    }
}
