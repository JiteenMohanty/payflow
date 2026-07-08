package com.payflow.core.dashboard.api;

import java.math.BigDecimal;
import java.util.List;

public record DashboardWindowSummaryResponse(String window, long paymentCount, BigDecimal totalVolume, List<DashboardStatusCountResponse> byStatus) {
}
