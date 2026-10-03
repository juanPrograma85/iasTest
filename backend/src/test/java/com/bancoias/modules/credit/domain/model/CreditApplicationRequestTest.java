package com.bancoias.modules.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bancoias.shared.domain.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreditApplicationRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void rejectsNonPositiveAmount(String amount) {
        assertThatThrownBy(() -> new CreditApplicationRequest("REF-1", "CLI-1001", new BigDecimal(amount), 24))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("INVALID_AMOUNT");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 61, 120})
    void rejectsTermOutOfRange(int term) {
        assertThatThrownBy(() -> new CreditApplicationRequest("REF-1", "CLI-1001", BigDecimal.TEN, term))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("INVALID_TERM");
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 24, 60})
    void acceptsTermWithinRange(int term) {
        assertThat(new CreditApplicationRequest("REF-1", "CLI-1001", BigDecimal.TEN, term).termMonths()).isEqualTo(term);
    }

    @Test
    void comparesRelevantDataIgnoringAmountScale() {
        var original = CreditApplication.approve(
                new CreditApplicationRequest("REF-1", "CLI-1001", new BigDecimal("6000000.00"), 24), Instant.now());

        assertThat(new CreditApplicationRequest("REF-1", "CLI-1001", new BigDecimal("6000000"), 24)
                .hasSameDataAs(original)).isTrue();
        assertThat(new CreditApplicationRequest("REF-1", "CLI-1001", new BigDecimal("7000000"), 24)
                .hasSameDataAs(original)).isFalse();
        assertThat(new CreditApplicationRequest("REF-1", "CLI-2001", new BigDecimal("6000000"), 24)
                .hasSameDataAs(original)).isFalse();
        assertThat(new CreditApplicationRequest("REF-1", "CLI-1001", new BigDecimal("6000000"), 36)
                .hasSameDataAs(original)).isFalse();
    }

    @Test
    void rejectedApplicationCannotUseApprovalReason() {
        var request = new CreditApplicationRequest("REF-1", "CLI-1001", BigDecimal.TEN, 24);
        assertThatThrownBy(() -> CreditApplication.reject(request, DecisionReason.CREDIT_LIMIT_AVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
