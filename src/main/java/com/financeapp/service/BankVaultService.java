package com.financeapp.service;

import com.financeapp.model.BankVault;
import com.financeapp.model.VaultTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface BankVaultService {
    BigDecimal getVaultBalance();
    BankVault getOrCreateVault();
    VaultTransaction recordDeposit(BigDecimal amount, String customerName, String accountNumber, String note);
    VaultTransaction recordWithdrawal(BigDecimal amount, String customerName, String accountNumber, String note);
    VaultTransaction recordWithdrawal(BigDecimal eurAmount, BigDecimal originalAmount, String currency, String customerName, String accountNumber, String note);
    VaultTransaction recordVaultInflow(BigDecimal amount, String source, String note);
    VaultTransaction recordVaultInflow(BigDecimal eurAmount, BigDecimal originalAmount, String currency, String source, String note);
    VaultTransaction recordTransferOut(BigDecimal amount, BigDecimal commissionFee, String senderName, String senderAccount, String recipientName, String recipientAccount, String note);
    VaultTransaction recordCommissionRevenue(BigDecimal commissionAmount, BigDecimal grossAmount, String senderInfo, String recipientInfo);
    VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal amount, BigDecimal interestRate, String note);
    VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal inputAmount, String inputCurrency, BigDecimal interestRate, String note);
    VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal amount, String note);
    VaultTransaction transferToCustomer(String accountNumber, BigDecimal amount, String note);
    List<VaultTransaction> getAllTransactions();
}
