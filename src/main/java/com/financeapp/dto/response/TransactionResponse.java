package com.financeapp.dto.response;

import java.math.BigDecimal;

public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private String description;

    public TransactionResponse() {}

    public TransactionResponse(Long id, BigDecimal amount, String description) {
        this.id = id;
        this.amount = amount;
        this.description = description;
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }

    public void setId(Long id) { this.id = id; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setDescription(String description) { this.description = description; }

    public static TransactionResponseBuilder builder() {
        return new TransactionResponseBuilder();
    }

    public static class TransactionResponseBuilder {
        private Long id;
        private BigDecimal amount;
        private String description;

        public TransactionResponseBuilder id(Long id) { this.id = id; return this; }
        public TransactionResponseBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public TransactionResponseBuilder description(String description) { this.description = description; return this; }

        public TransactionResponse build() {
            return new TransactionResponse(id, amount, description);
        }
    }

    @Override
    public String toString() {
        return "TransactionResponse{id=" + id + ", amount=" + amount + ", description='" + description + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionResponse)) return false;
        TransactionResponse that = (TransactionResponse) o;
        return java.util.Objects.equals(id, that.id) &&
               java.util.Objects.equals(amount, that.amount) &&
               java.util.Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, amount, description);
    }
}
