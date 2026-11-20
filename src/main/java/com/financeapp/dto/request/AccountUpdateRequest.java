package com.financeapp.dto.request;

import com.financeapp.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AccountUpdateRequest {

    @NotBlank(message = "Valyuta boş ola bilməz")
    private String currency;

    @NotNull(message = "Status boş ola bilməz")
    private AccountStatus status;

    public AccountUpdateRequest() {}

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
}
