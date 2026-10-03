package com.bancoias.modules.credit.presentation.http.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bancoias.config.ClockConfig;
import com.bancoias.modules.credit.application.usecases.GetCreditApplicationUseCase;
import com.bancoias.modules.credit.application.usecases.GetRecentCreditApplicationsUseCase;
import com.bancoias.modules.credit.application.usecases.ProcessCreditApplicationUseCase;
import com.bancoias.shared.domain.ConflictException;
import com.bancoias.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(CreditApplicationController.class)
@Import(ClockConfig.class)
class CreditApplicationControllerTest {

    private static final String URL = "/api/v1/credit-applications";

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ProcessCreditApplicationUseCase processUseCase;
    @MockitoBean
    private GetCreditApplicationUseCase getUseCase;
    @MockitoBean
    private GetRecentCreditApplicationsUseCase recentUseCase;

    @Test
    void malformedJsonReturns400() {
        post("{\"applicationReference\": ")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void missingFieldsReturn400() {
        post("{\"applicationReference\":\"REF-1\"}")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void nonPositiveAmountReturns422WithoutProcessing() {
        post(body("REF-1", "CLI-1001", "0", 24))
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_AMOUNT");
        verify(processUseCase, never()).execute(any());
    }

    @Test
    void termOutOfRangeReturns422() {
        post(body("REF-1", "CLI-1001", "1000", 61))
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_TERM");
    }

    @Test
    void conflictReturns409() {
        when(processUseCase.execute(any())).thenReturn(Mono.error(new ConflictException("APPLICATION_REFERENCE_CONFLICT", "x")));

        post(body("REF-1", "CLI-1001", "1000", 24))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("APPLICATION_REFERENCE_CONFLICT")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void unknownReferenceReturns404() {
        when(getUseCase.execute("REF-X")).thenReturn(Mono.error(new ResourceNotFoundException("CREDIT_APPLICATION_NOT_FOUND", "x")));

        client.get().uri(URL + "/REF-X").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CREDIT_APPLICATION_NOT_FOUND");
    }

    @Test
    void invalidRecentLimitReturns400() {
        client.get().uri(URL + "/recent?limit=0").exchange().expectStatus().isBadRequest();
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        when(processUseCase.execute(any())).thenReturn(Mono.error(new IllegalStateException("db password leaked")));

        post(body("REF-1", "CLI-1001", "1000", 24))
                .expectStatus().is5xxServerError()
                .expectBody().jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo("Ocurrió un error inesperado. Intente nuevamente.");
    }

    private WebTestClient.ResponseSpec post(String json) {
        return client.post().uri(URL).contentType(MediaType.APPLICATION_JSON).bodyValue(json).exchange();
    }

    private static String body(String ref, String customer, String amount, int term) {
        return """
                {"applicationReference":"%s","customerId":"%s","amount":%s,"termMonths":%d}
                """.formatted(ref, customer, amount, term);
    }
}
