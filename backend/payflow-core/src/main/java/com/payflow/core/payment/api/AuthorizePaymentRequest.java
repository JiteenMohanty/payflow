package com.payflow.core.payment.api;

import java.util.UUID;

public record AuthorizePaymentRequest(UUID providerAccountId) {
}
