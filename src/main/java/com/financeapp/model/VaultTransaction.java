package com.financeapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vault_transactions")
public class VaultTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String source;

    private String category;

    private String note;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public VaultTransaction() {
        this.timestamp = LocalDateTime.now();
    }

    public VaultTransaction(BigDecimal amount, String type, String source, String category, String note, BigDecimal balanceAfter) {
        this.amount = amount;
        this.type = type;
        this.source = source;
        this.category = category;
        this.note = note;
        this.balanceAfter = balanceAfter;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getDisplayAmount() {
        return originalAmount != null ? originalAmount : amount;
    }

    public String getCurrencySymbol() {
        return com.financeapp.service.CurrencyConverter.symbol(currency != null && !currency.isBlank() ? currency : "EUR");
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getTransactionType() { return type; }
    public String getSourceOrRecipient() { return source; }
    public BigDecimal getRunningBalance() { return balanceAfter; }
    public LocalDateTime getCreatedAt() { return timestamp; }
}
