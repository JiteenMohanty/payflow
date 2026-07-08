package com.payflow.core.payment.application;

import java.math.BigDecimal;
import java.util.List;

public record DashboardWindowSummary(String window, long paymentCount, BigDecimal totalVolume, List<DashboardStatusCount> byStatus) {
}
