package com.bancoias.modules.credit.presentation.http.dto;

import java.util.List;

public record RecentCreditApplicationsResponse(List<CreditApplicationResponse> items) {
}
