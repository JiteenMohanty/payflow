package com.payflow.core.webhook.outbound.application;

import com.payflow.core.common.exception.DomainValidationException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Registration-time SSRF defense (EDD risk table: "Merchant-supplied
 * webhook URL used for SSRF ... URL validation on registration, public DNS
 * resolution required, block RFC1918/loopback/link-local ranges"). The
 * complementary delivery-time defense - denying HTTP redirects - lives in
 * WebhookDispatcher's own client configuration, since DNS can change
 * between registration and delivery but a delivery that never follows a
 * redirect at all doesn't need to re-resolve to stay safe.
 */
@Component
public class WebhookUrlValidator {

    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new DomainValidationException("Invalid webhook URL: " + url);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new DomainValidationException("Webhook URL must use https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new DomainValidationException("Webhook URL must include a host");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DomainValidationException("Webhook URL host could not be resolved: " + host);
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                throw new DomainValidationException(
                        "Webhook URL resolves to a disallowed private/loopback/link-local address: " + address.getHostAddress());
            }
        }
    }
}
