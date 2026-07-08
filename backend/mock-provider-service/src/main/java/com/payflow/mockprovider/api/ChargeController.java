package com.payflow.mockprovider.api;

import com.payflow.mockprovider.ChargeStore;
import com.payflow.mockprovider.domain.MockCharge;
import com.payflow.mockprovider.domain.MockChargeStatus;
import com.payflow.mockprovider.webhook.WebhookSender;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic, always-succeeds behavior for M2. Configurable
 * latency/failure/retry simulation is M11 (Mock Provider hardening). Every
 * successful operation also fires an async signed webhook, regardless of
 * the synchronous response - see EDD section 5.3.
 */
@RestController
@RequestMapping("/provider/v1/charges")
public class ChargeController {

    private final ChargeStore chargeStore;
    private final WebhookSender webhookSender;

    public ChargeController(ChargeStore chargeStore, WebhookSender webhookSender) {
        this.chargeStore = chargeStore;
        this.webhookSender = webhookSender;
    }

    @PostMapping
    public ResponseEntity<ChargeResponse> createCharge(@RequestBody ChargeRequest request) {
        MockCharge charge = chargeStore.create(request.amount(), request.currency());
        webhookSender.sendAsync("charge.authorized", charge.getChargeId(), charge.getAmount(), charge.getCurrency());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ChargeResponse(charge.getChargeId(), "AUTHORIZED", null));
    }

    @PostMapping("/{chargeId}/capture")
    public ResponseEntity<CaptureResponse> capture(
            @PathVariable String chargeId,
            @RequestBody CaptureRequest request) {
        MockCharge charge = chargeStore.find(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found: " + chargeId));

        if (charge.getStatus() != MockChargeStatus.AUTHORIZED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Charge is not in a capturable state: " + chargeId);
        }

        chargeStore.markCaptured(chargeId);
        webhookSender.sendAsync("charge.captured", chargeId, request.amount(), request.currency());
        return ResponseEntity.ok(new CaptureResponse("CAPTURED", null));
    }

    @PostMapping("/{chargeId}/refund")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable String chargeId,
            @RequestBody RefundRequest request) {
        MockCharge charge = chargeStore.find(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found: " + chargeId));

        if (charge.getStatus() != MockChargeStatus.CAPTURED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Charge is not in a refundable state: " + chargeId);
        }

        webhookSender.sendAsync("charge.refunded", chargeId, request.amount(), request.currency());
        return ResponseEntity.ok(new RefundResponse("REFUNDED", null));
    }

    @GetMapping("/{chargeId}")
    public ResponseEntity<ChargeStatusResponse> getStatus(@PathVariable String chargeId) {
        MockCharge charge = chargeStore.find(chargeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found: " + chargeId));

        return ResponseEntity.ok(new ChargeStatusResponse(charge.getStatus().name(), charge.getAmount(), charge.getCurrency()));
    }
}
