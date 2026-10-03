package com.bancoias.modules.credit.presentation.http.controllers;

import com.bancoias.modules.credit.application.usecases.GetCreditApplicationUseCase;
import com.bancoias.modules.credit.application.usecases.GetRecentCreditApplicationsUseCase;
import com.bancoias.modules.credit.application.usecases.ProcessCreditApplicationUseCase;
import com.bancoias.modules.credit.presentation.http.dto.CreateCreditApplicationRequest;
import com.bancoias.modules.credit.presentation.http.dto.CreditApplicationResponse;
import com.bancoias.modules.credit.presentation.http.dto.RecentCreditApplicationsResponse;
import com.bancoias.shared.infrastructure.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(CreditApplicationController.BASE_PATH)
@Tag(name = "Credit Applications")
public class CreditApplicationController {

    static final String BASE_PATH = "/api/v1/credit-applications";

    private final ProcessCreditApplicationUseCase processUseCase;
    private final GetCreditApplicationUseCase getUseCase;
    private final GetRecentCreditApplicationsUseCase recentUseCase;

    public CreditApplicationController(ProcessCreditApplicationUseCase processUseCase,
                                       GetCreditApplicationUseCase getUseCase,
                                       GetRecentCreditApplicationsUseCase recentUseCase) {
        this.processUseCase = processUseCase;
        this.getUseCase = getUseCase;
        this.recentUseCase = recentUseCase;
    }

    @PostMapping
    @Operation(summary = "Procesa una solicitud de crédito (idempotente por applicationReference)")
    @ApiResponse(responseCode = "201", description = "Solicitud procesada (APPROVED o REJECTED)")
    @ApiResponse(responseCode = "200", description = "Repetición idéntica: se devuelve el resultado original")
    @ApiResponse(responseCode = "400", description = "Formato inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Referencia reutilizada con datos diferentes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Monto, plazo o cliente inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Mono<ResponseEntity<CreditApplicationResponse>> process(
            @Valid @RequestBody CreateCreditApplicationRequest body) {
        return Mono.fromCallable(body::toDomain)
                .flatMap(processUseCase::execute)
                .map(result -> {
                    var response = CreditApplicationResponse.of(result.application(), result.idempotentReplay());
                    return result.idempotentReplay()
                            ? ResponseEntity.ok(response)
                            : ResponseEntity.created(URI.create(BASE_PATH + "/" + response.applicationReference()))
                                    .body(response);
                });
    }

    @GetMapping("/recent")
    @Operation(summary = "Solicitudes procesadas recientemente (processedAt DESC)")
    public Mono<RecentCreditApplicationsResponse> recent(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return recentUseCase.execute(limit)
                .map(CreditApplicationResponse::of)
                .collectList()
                .map(RecentCreditApplicationsResponse::new);
    }

    @GetMapping("/{applicationReference}")
    @Operation(summary = "Consulta una solicitud por applicationReference")
    @ApiResponse(responseCode = "200", description = "Solicitud encontrada")
    @ApiResponse(responseCode = "404", description = "Solicitud inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Mono<CreditApplicationResponse> getByReference(@PathVariable String applicationReference) {
        return getUseCase.execute(applicationReference).map(CreditApplicationResponse::of);
    }
}
