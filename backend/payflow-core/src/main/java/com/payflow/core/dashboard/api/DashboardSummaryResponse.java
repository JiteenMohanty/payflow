package com.payflow.core.dashboard.api;

import java.util.List;

public record DashboardSummaryResponse(List<DashboardWindowSummaryResponse> windows) {
}
