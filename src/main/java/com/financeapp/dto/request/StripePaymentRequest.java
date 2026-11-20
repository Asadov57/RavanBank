package com.financeapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class StripePaymentRequest {

    @NotBlank(message = "Hesab nömrəsi boş ola bilməz")
    private String accountNumber;

    @NotNull(message = "Məbləğ boş ola bilməz")
    @Positive(message = "Məbləğ müsbət olmalıdır")
    private BigDecimal amount;

    private String paymentMethodId;

    private String description;

    public StripePaymentRequest() {}

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
