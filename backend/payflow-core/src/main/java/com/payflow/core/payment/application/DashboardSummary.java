package com.payflow.core.payment.application;

import java.util.List;

/**
 * EDD section 5.2: "Volume, status breakdown, last 24h/7d/30d" - one window
 * per fixed lookback, each independently aggregated (a payment captured 20
 * hours ago counts toward all three windows, not just the shortest one that
 * includes it).
 */
public record DashboardSummary(List<DashboardWindowSummary> windows) {
}
