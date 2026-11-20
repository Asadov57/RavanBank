package com.financeapp.service;

import com.financeapp.dto.request.StripePaymentRequest;
import com.financeapp.dto.response.StripePaymentResponse;

public interface StripeService {

    StripePaymentResponse createPaymentIntent(StripePaymentRequest request);

    void confirmDepositByPaymentIntent(String paymentIntentId);
}
