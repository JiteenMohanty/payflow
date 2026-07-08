package com.payflow.core.dashboard.api;

import java.util.List;

public record DashboardPaymentDetailResponse(DashboardPaymentResponse payment, List<DashboardPaymentTransitionResponse> transitions) {
}
