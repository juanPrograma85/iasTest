package com.bancoias.modules.credit.application.usecases;

import com.bancoias.modules.credit.domain.model.CreditApplication;

public record ProcessingResult(CreditApplication application, boolean idempotentReplay) {
}
