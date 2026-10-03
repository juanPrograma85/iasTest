package com.bancoias.config;

import com.bancoias.modules.credit.domain.services.CreditApprovalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public CreditApprovalPolicy creditApprovalPolicy() {
        return new CreditApprovalPolicy();
    }
}
