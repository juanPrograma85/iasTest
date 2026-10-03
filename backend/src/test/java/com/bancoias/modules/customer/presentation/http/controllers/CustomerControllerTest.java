package com.bancoias.modules.customer.presentation.http.controllers;

import static org.mockito.Mockito.when;

import com.bancoias.config.ClockConfig;
import com.bancoias.modules.customer.application.usecases.FindCustomerUseCase;
import com.bancoias.modules.customer.domain.model.Customer;
import com.bancoias.modules.customer.domain.model.CustomerStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(CustomerController.class)
@Import(ClockConfig.class)
class CustomerControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private FindCustomerUseCase findCustomer;

    @Test
    void returnsStatusWithoutExposingAmounts() {
        when(findCustomer.execute("CLI-1002")).thenReturn(Mono.just(
                new Customer("CLI-1002", CustomerStatus.BLOCKED, new BigDecimal("8000000"), BigDecimal.ZERO)));

        client.get().uri("/api/v1/customers/CLI-1002").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.customerId").isEqualTo("CLI-1002")
                .jsonPath("$.status").isEqualTo("BLOCKED")
                .jsonPath("$.maxApprovedAmount").doesNotExist()
                .jsonPath("$.currentApprovedAmount").doesNotExist();
    }

    @Test
    void unknownCustomerReturns404() {
        when(findCustomer.execute("ZZZZZ")).thenReturn(Mono.empty());

        client.get().uri("/api/v1/customers/ZZZZZ").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CUSTOMER_NOT_FOUND")
                .jsonPath("$.message").isEqualTo("El cliente no existe.");
    }
}
