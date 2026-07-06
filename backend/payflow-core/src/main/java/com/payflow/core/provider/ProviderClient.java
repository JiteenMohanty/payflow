package com.payflow.core.provider;

import com.payflow.core.common.provider.ProviderCode;

public interface ProviderClient {

    ProviderCode providerCode();

    ProviderAuthorizationResult authorize(ProviderAuthorizationRequest request);

    ProviderCaptureResult capture(ProviderCaptureRequest request);
}
