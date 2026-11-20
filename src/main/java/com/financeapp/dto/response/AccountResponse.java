package com.financeapp.dto.response;

import com.financeapp.enums.AccountStatus;
import java.math.BigDecimal;

public class AccountResponse {

    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private String customerName;
    private String username;
    private String password;
    private String cvv;
    private String expiryDate;
    private AccountStatus status;
    private boolean transferEligible;
    private String accountType;
    private String finCode;

    public AccountResponse() {}

    public AccountResponse(Long id, String accountNumber, BigDecimal balance,
                           String currency, AccountStatus status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.transferEligible = (status == AccountStatus.ACTIVE);
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public String getCustomerName() { return customerName; }
    public String getUsername() { return username; }
    public String getPassword() { return password != null ? password : ""; }
    public String getCvv() {
        if (cvv != null && !cvv.trim().isEmpty()) {
            return cvv;
        }
        if (accountNumber != null) {
            int hash = Math.abs(accountNumber.hashCode());
            int val = 100 + (hash % 900);
            return String.valueOf(val);
        }
        return "742";
    }
    public void setCvv(String cvv) { this.cvv = cvv; }

    public String getExpiryDate() {
        if (expiryDate != null && !expiryDate.trim().isEmpty()) {
            return expiryDate;
        }
        if (accountNumber != null) {
            int hash = Math.abs(accountNumber.hashCode());
            int month = (hash % 12) + 1;
            int year = 28 + ((hash / 12) % 3);
            return String.format("%02d/%02d", month, year);
        }
        return "08/28";
    }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getFormattedCardNumber() {
        if (accountNumber == null) return "4098 7056 4547 6397";
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() == 16) {
            return digits.substring(0, 4) + " " + digits.substring(4, 8) + " " + digits.substring(8, 12) + " " + digits.substring(12, 16);
        }
        return accountNumber;
    }
    public AccountStatus getStatus() { return status; }
    public boolean isTransferEligible() { return transferEligible; }
    public String getAccountType() { return accountType != null ? accountType : "NORMAL"; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getFinCode() { return finCode; }
    public void setFinCode(String finCode) { this.finCode = finCode; }

    public String getMaskedAccountNumber() {
        if (accountNumber == null) return "**** **** **** ****";
        String digits = accountNumber.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            String last4 = digits.substring(digits.length() - 4);
            return "**** **** **** " + last4;
        }
        if (accountNumber.length() >= 4) {
            String last4 = accountNumber.substring(accountNumber.length() - 4);
            return "**** **** **** " + last4;
        }
        return "**** **** **** " + accountNumber;
    }

    public void setId(Long id) { this.id = id; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setStatus(AccountStatus status) {
        this.status = status;
        this.transferEligible = (status == AccountStatus.ACTIVE);
    }
    public void setTransferEligible(boolean transferEligible) {
        this.transferEligible = transferEligible;
    }

    @Override
    public String toString() {
        return "AccountResponse{id=" + id + ", accountNumber='" + accountNumber +
               "', balance=" + balance + ", currency='" + currency +
               "', status=" + status + "}";
    }
}
