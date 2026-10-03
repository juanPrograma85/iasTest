package com.bancoias;

import static org.assertj.core.api.Assertions.assertThat;

import com.bancoias.modules.credit.application.usecases.ProcessCreditApplicationUseCase;
import com.bancoias.modules.credit.application.usecases.ProcessingResult;
import com.bancoias.modules.credit.domain.model.CreditApplicationRequest;
import com.bancoias.modules.credit.domain.model.CreditApplicationStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CreditApplicationIntegrationTest {

    private static final String URL = "/api/v1/credit-applications";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bancoias")
            .withUsername("bancoias")
            .withPassword("test")
            // Mismas migraciones que usa docker-compose.
            .withCopyFileToContainer(MountableFile.forHostPath("../db/migrations"), "/docker-entrypoint-initdb.d/");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/bancoias"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebTestClient client;
    @Autowired
    private ProcessCreditApplicationUseCase processUseCase;
    @Autowired
    private DatabaseClient db;

    @BeforeEach
    void resetState() {
        db.sql("TRUNCATE credit.credit_applications").then()
                .then(db.sql("UPDATE customer.customers SET current_approved_amount = 0").then())
                .block();
    }

    @RepeatedTest(5)
    void concurrentRequestsNeverExceedCreditLimit() {
        var results = runConcurrently(
                request("C-A", "CLI-1001", "6000000"),
                request("C-B", "CLI-1001", "6000000"));

        assertThat(results).extracting(r -> r.application().status())
                .containsExactlyInAnyOrder(CreditApplicationStatus.APPROVED, CreditApplicationStatus.REJECTED);
        assertThat(usedLimit("CLI-1001")).isEqualByComparingTo("6000000");
    }

    @Test
    void concurrentRequestsWithinLimitAreAllApproved() {
        var results = runConcurrently(
                request("C-A", "CLI-1001", "6000000"),
                request("C-B", "CLI-1001", "4000000"));

        assertThat(results).allMatch(r -> r.application().status() == CreditApplicationStatus.APPROVED);
        assertThat(usedLimit("CLI-1001")).isEqualByComparingTo("10000000");
    }

    @Test
    void manyConcurrentRequestsApproveExactlyUpToTheLimit() {
        var requests = IntStream.range(0, 12)
                .mapToObj(i -> request("C-" + i, "CLI-1001", "2000000"))
                .toArray(CreditApplicationRequest[]::new);

        var results = runConcurrently(requests);

        assertThat(results).filteredOn(r -> r.application().status() == CreditApplicationStatus.APPROVED).hasSize(5);
        assertThat(usedLimit("CLI-1001")).isEqualByComparingTo("10000000");
        assertThat(countApplications()).isEqualTo(12);
    }

    @RepeatedTest(5)
    void concurrentSameReferenceIsProcessedOnce() {
        var results = runConcurrently(
                request("REF-DUP", "CLI-2001", "6000000"),
                request("REF-DUP", "CLI-2001", "6000000"));

        assertThat(results).allMatch(r -> r.application().status() == CreditApplicationStatus.APPROVED);
        assertThat(results).filteredOn(ProcessingResult::idempotentReplay).hasSize(1);
        assertThat(usedLimit("CLI-2001")).isEqualByComparingTo("6000000");
        assertThat(countApplications()).isEqualTo(1);
    }

    @Test
    void apiProcessesReplaysAndRejectsConflictingReference() {
        post("REF-001", "CLI-1001", 6000000, 24).expectStatus().isCreated()
                .expectHeader().location(URL + "/REF-001")
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.decisionReason").isEqualTo("CREDIT_LIMIT_AVAILABLE")
                .jsonPath("$.idempotentReplay").isEqualTo(false)
                .jsonPath("$.processedAt").exists();

        post("REF-001", "CLI-1001", 6000000, 24).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.idempotentReplay").isEqualTo(true);

        post("REF-001", "CLI-1001", 7000000, 24).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("APPLICATION_REFERENCE_CONFLICT");

        assertThat(usedLimit("CLI-1001")).isEqualByComparingTo("6000000");
        client.get().uri(URL + "/REF-001").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.amount").isEqualTo(6000000.00);
    }

    @Test
    void apiPersistsBusinessRejectionsWithoutConsumingLimit() {
        post("REF-B", "CLI-1002", 1000000, 12).expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.decisionReason").isEqualTo("CUSTOMER_NOT_ELIGIBLE");

        post("REF-L", "CLI-1001", 10000001, 12).expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.decisionReason").isEqualTo("CREDIT_LIMIT_EXCEEDED");

        assertThat(usedLimit("CLI-1002")).isEqualByComparingTo("0");
        assertThat(usedLimit("CLI-1001")).isEqualByComparingTo("0");
        assertThat(countApplications()).isEqualTo(2);
    }

    @Test
    void apiReturns422ForUnknownCustomerWithoutPersisting() {
        post("REF-U", "CLI-9999", 1000, 12).expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(countApplications()).isZero();
        client.get().uri(URL + "/REF-U").exchange().expectStatus().isNotFound();
    }

    @Test
    void apiListsRecentApplicationsNewestFirst() {
        post("REF-1", "CLI-2001", 1000000, 12).expectStatus().isCreated();
        post("REF-2", "CLI-2001", 2000000, 12).expectStatus().isCreated();
        post("REF-3", "CLI-1002", 3000000, 12).expectStatus().isCreated();

        client.get().uri(URL + "/recent?limit=2").exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.items[0].applicationReference").isEqualTo("REF-3")
                .jsonPath("$.items[1].applicationReference").isEqualTo("REF-2");
    }

    private List<ProcessingResult> runConcurrently(CreditApplicationRequest... requests) {
        return Flux.fromArray(requests)
                .flatMap(r -> Mono.defer(() -> processUseCase.execute(r)).subscribeOn(Schedulers.parallel()))
                .collectList()
                .block();
    }

    private WebTestClient.ResponseSpec post(String ref, String customer, long amount, int term) {
        return client.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"applicationReference":"%s","customerId":"%s","amount":%d,"termMonths":%d}
                        """.formatted(ref, customer, amount, term))
                .exchange();
    }

    private BigDecimal usedLimit(String customerId) {
        return db.sql("SELECT current_approved_amount FROM customer.customers WHERE customer_id = :id")
                .bind("id", customerId)
                .map(row -> row.get(0, BigDecimal.class))
                .one()
                .block();
    }

    private long countApplications() {
        return db.sql("SELECT count(*) FROM credit.credit_applications")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
    }

    private static CreditApplicationRequest request(String ref, String customerId, String amount) {
        return new CreditApplicationRequest(ref, customerId, new BigDecimal(amount), 24);
    }
}
