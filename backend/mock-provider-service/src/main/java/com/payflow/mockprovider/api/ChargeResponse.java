package com.payflow.mockprovider.api;

public record ChargeResponse(String chargeId, String status, String declineReason) {
}
