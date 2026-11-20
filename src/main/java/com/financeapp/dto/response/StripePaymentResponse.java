package com.financeapp.dto.response;

import java.math.BigDecimal;

public class StripePaymentResponse {

    private String paymentIntentId;
    private String clientSecret;
    private String status;
    private BigDecimal amount;
    private String accountNumber;
    private String message;

    public StripePaymentResponse() {}

    public StripePaymentResponse(String paymentIntentId, String clientSecret,
                                  String status, BigDecimal amount,
                                  String accountNumber, String message) {
        this.paymentIntentId = paymentIntentId;
        this.clientSecret = clientSecret;
        this.status = status;
        this.amount = amount;
        this.accountNumber = accountNumber;
        this.message = message;
    }

    public String getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
