package com.financeapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private String senderAccountNumber;
    private String recipientAccountNumber;
    private String recipientName;
    private BigDecimal grossAmount;
    private BigDecimal commissionFee;
    private BigDecimal netAmount;
    private BigDecimal totalDeducted;
    private String referenceNumber;
    private LocalDateTime timestamp;
    private String sentCurrency;
    private String recipientCurrency;
    private BigDecimal convertedAmount;

    public String getSentCurrency() { return sentCurrency; }
    public void setSentCurrency(String sentCurrency) { this.sentCurrency = sentCurrency; }

    public String getRecipientCurrency() { return recipientCurrency; }
    public void setRecipientCurrency(String recipientCurrency) { this.recipientCurrency = recipientCurrency; }

    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public void setConvertedAmount(BigDecimal convertedAmount) { this.convertedAmount = convertedAmount; }

    public String getSenderAccountNumber() { return senderAccountNumber; }
    public void setSenderAccountNumber(String senderAccountNumber) { this.senderAccountNumber = senderAccountNumber; }

    public String getRecipientAccountNumber() { return recipientAccountNumber; }
    public void setRecipientAccountNumber(String recipientAccountNumber) { this.recipientAccountNumber = recipientAccountNumber; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getCommissionFee() { return commissionFee; }
    public void setCommissionFee(BigDecimal commissionFee) { this.commissionFee = commissionFee; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public BigDecimal getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(BigDecimal totalDeducted) { this.totalDeducted = totalDeducted; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getMaskedRecipientAccountNumber() {
        if (recipientAccountNumber == null) return "**** **** **** ****";
        String digits = recipientAccountNumber.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "**** **** **** " + digits.substring(digits.length() - 4);
        }
        if (recipientAccountNumber.length() >= 4) {
            return "**** **** **** " + recipientAccountNumber.substring(recipientAccountNumber.length() - 4);
        }
        return "**** **** **** " + recipientAccountNumber;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
