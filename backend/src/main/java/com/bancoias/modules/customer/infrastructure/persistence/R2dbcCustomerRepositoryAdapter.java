package com.bancoias.modules.customer.infrastructure.persistence;

import com.bancoias.modules.customer.domain.model.Customer;
import com.bancoias.modules.customer.domain.model.CustomerStatus;
import com.bancoias.modules.customer.domain.ports.CustomerRepositoryPort;
import java.math.BigDecimal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcCustomerRepositoryAdapter implements CustomerRepositoryPort {

    private static final String FIND_BY_CUSTOMER_ID = """
            SELECT customer_id, status, max_approved_amount, current_approved_amount
              FROM customer.customers
             WHERE customer_id = :customerId
            """;

    // El WHERE se re-evalúa tras obtener el lock de fila: serializa aprobaciones concurrentes del mismo cliente.
    private static final String TRY_INCREASE_APPROVED_AMOUNT = """
            UPDATE customer.customers
               SET current_approved_amount = current_approved_amount + :amount,
                   updated_at = CURRENT_TIMESTAMP
             WHERE customer_id = :customerId
               AND status = 'ELIGIBLE'
               AND current_approved_amount + :amount <= max_approved_amount
            """;

    private final DatabaseClient databaseClient;

    public R2dbcCustomerRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Customer> findByCustomerId(String customerId) {
        return databaseClient.sql(FIND_BY_CUSTOMER_ID)
                .bind("customerId", customerId)
                .map((row, metadata) -> new Customer(
                        row.get("customer_id", String.class),
                        CustomerStatus.valueOf(row.get("status", String.class)),
                        row.get("max_approved_amount", BigDecimal.class),
                        row.get("current_approved_amount", BigDecimal.class)))
                .one();
    }

    @Override
    public Mono<Boolean> tryIncreaseApprovedAmount(String customerId, BigDecimal amount) {
        return databaseClient.sql(TRY_INCREASE_APPROVED_AMOUNT)
                .bind("customerId", customerId)
                .bind("amount", amount)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows == 1);
    }
}
