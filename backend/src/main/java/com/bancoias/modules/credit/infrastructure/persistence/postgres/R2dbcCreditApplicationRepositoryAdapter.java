package com.bancoias.modules.credit.infrastructure.persistence.postgres;

import com.bancoias.modules.credit.domain.model.CreditApplication;
import com.bancoias.modules.credit.domain.model.CreditApplicationStatus;
import com.bancoias.modules.credit.domain.model.DecisionReason;
import com.bancoias.modules.credit.domain.ports.CreditApplicationRepositoryPort;
import com.bancoias.modules.credit.domain.ports.DuplicateApplicationReferenceException;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcCreditApplicationRepositoryAdapter implements CreditApplicationRepositoryPort {

    private static final String COLUMNS =
            "application_reference, customer_id, amount, term_months, status, decision_reason, processed_at";

    private static final String FIND_BY_REFERENCE =
            "SELECT " + COLUMNS + " FROM credit.credit_applications WHERE application_reference = :reference";

    private static final String FIND_RECENT =
            "SELECT " + COLUMNS + " FROM credit.credit_applications ORDER BY processed_at DESC, id DESC LIMIT :limit";

    private static final String INSERT = """
            INSERT INTO credit.credit_applications
                (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
            VALUES (:reference, :customerId, :amount, :termMonths, :status, :decisionReason, :processedAt)
            """;

    private final DatabaseClient databaseClient;

    public R2dbcCreditApplicationRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<CreditApplication> findByApplicationReference(String applicationReference) {
        return databaseClient.sql(FIND_BY_REFERENCE)
                .bind("reference", applicationReference)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Mono<CreditApplication> save(CreditApplication application) {
        return databaseClient.sql(INSERT)
                .bind("reference", application.applicationReference())
                .bind("customerId", application.customerId())
                .bind("amount", application.amount())
                .bind("termMonths", application.termMonths())
                .bind("status", application.status().name())
                .bind("decisionReason", application.decisionReason().name())
                .bind("processedAt", OffsetDateTime.ofInstant(application.processedAt(), ZoneOffset.UTC))
                .then()
                .onErrorMap(DuplicateKeyException.class,
                        e -> new DuplicateApplicationReferenceException(application.applicationReference(), e))
                .thenReturn(application);
    }

    @Override
    public Flux<CreditApplication> findRecent(int limit) {
        return databaseClient.sql(FIND_RECENT)
                .bind("limit", limit)
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    private static CreditApplication toDomain(Row row) {
        return new CreditApplication(
                row.get("application_reference", String.class),
                row.get("customer_id", String.class),
                row.get("amount", BigDecimal.class),
                row.get("term_months", Integer.class),
                CreditApplicationStatus.valueOf(row.get("status", String.class)),
                DecisionReason.valueOf(row.get("decision_reason", String.class)),
                row.get("processed_at", OffsetDateTime.class).toInstant());
    }
}
