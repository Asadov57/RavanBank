package com.financeapp.controller;

import com.financeapp.dto.request.StripePaymentRequest;
import com.financeapp.dto.response.StripePaymentResponse;
import com.financeapp.service.StripeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private final StripeService stripeService;

    public StripeController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/create-payment-intent")
    public ResponseEntity<StripePaymentResponse> createPaymentIntent(
            @Valid @RequestBody StripePaymentRequest request) {
        StripePaymentResponse response = stripeService.createPaymentIntent(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm/{paymentIntentId}")
    public ResponseEntity<String> confirmDeposit(
            @PathVariable String paymentIntentId) {
        stripeService.confirmDepositByPaymentIntent(paymentIntentId);
        return ResponseEntity.ok("Balans uğurla artırıldı. PaymentIntent: " + paymentIntentId);
    }
}
