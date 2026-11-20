package com.financeapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class TransactionCreateRequest {

    @NotNull(message = "Hesab ID boş ola bilməz")
    private Long accountId;

    @NotNull(message = "Məbləğ boş ola bilməz")
    @Positive(message = "Məbləğ müsbət olmalıdır")
    private BigDecimal amount;

    @NotBlank(message = "Tip boş ola bilməz (DEPOSIT / WITHDRAWAL)")
    private String type;

    private String description;

    public TransactionCreateRequest() {}

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
