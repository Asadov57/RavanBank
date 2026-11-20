package com.financeapp.service.impl;

import com.financeapp.exception.InsufficientBalanceException;
import com.financeapp.model.Account;
import com.financeapp.model.BankEmployee;
import com.financeapp.model.BankVault;
import com.financeapp.model.Transaction;
import com.financeapp.model.VaultTransaction;
import com.financeapp.repository.AccountRepository;
import com.financeapp.repository.BankEmployeeRepository;
import com.financeapp.repository.BankVaultRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.VaultTransactionRepository;
import com.financeapp.service.BankVaultService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BankVaultServiceImpl implements BankVaultService {

    private final BankVaultRepository bankVaultRepository;
    private final VaultTransactionRepository vaultTransactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BankEmployeeRepository bankEmployeeRepository;

    public BankVaultServiceImpl(BankVaultRepository bankVaultRepository,
                                VaultTransactionRepository vaultTransactionRepository,
                                AccountRepository accountRepository,
                                TransactionRepository transactionRepository,
                                BankEmployeeRepository bankEmployeeRepository) {
        this.bankVaultRepository = bankVaultRepository;
        this.vaultTransactionRepository = vaultTransactionRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
    }

    @Override
    @Transactional
    public BankVault getOrCreateVault() {
        return bankVaultRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> bankVaultRepository.save(new BankVault(new BigDecimal("10000000000.00"), "EUR")));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getVaultBalance() {
        return getOrCreateVault().getBalance();
    }

    @Override
    @Transactional
    public VaultTransaction recordDeposit(BigDecimal amount, String customerName, String accountNumber, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Depozit məbləği müsbət olmalıdır");
        }

        BankVault vault = getOrCreateVault();
        if (vault.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Bank büdcəsində yetərli vəsait yoxdur! Cari büdcə: €" + vault.getBalance());
        }

        vault.setBalance(vault.getBalance().subtract(amount));
        bankVaultRepository.save(vault);

        String source = "Müştəri Hesabına Depozit • " + (customerName != null && !customerName.trim().isEmpty() ? customerName.trim() : "Müştəri")
                + (accountNumber != null && !accountNumber.trim().isEmpty() ? " (" + accountNumber.trim() + ")" : "");
        String memo = (note != null && !note.trim().isEmpty()) ? note.trim() : "Bank büdcəsindən müştəri hesabına depozit ayrıldı";

        VaultTransaction vt = new VaultTransaction(
                amount,
                "OUTFLOW",
                source,
                "CUSTOMER_DEPOSIT",
                memo,
                vault.getBalance()
        );
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    public VaultTransaction recordVaultInflow(BigDecimal amount, String source, String note) {
        return recordVaultInflow(amount, amount, "EUR", source, note);
    }

    @Override
    @Transactional
    public VaultTransaction recordVaultInflow(BigDecimal eurAmount, BigDecimal originalAmount, String currency, String source, String note) {
        if (eurAmount == null || eurAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Məbləğ müsbət olmalıdır");
        }

        String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
        BankVault vault = getOrCreateVault();
        vault.setBalance(vault.getBalance().add(eurAmount));
        bankVaultRepository.save(vault);

        VaultTransaction vt = new VaultTransaction(
                eurAmount,
                "INFLOW",
                source != null && !source.trim().isEmpty() ? source.trim() : "Banka Mədaxil",
                "VAULT_INFLOW",
                note != null && !note.trim().isEmpty() ? note.trim() : "Mərkəzi büdcəyə vəsait mədaxil edildi",
                vault.getBalance()
        );
        vt.setOriginalAmount(originalAmount != null ? originalAmount : eurAmount);
        vt.setCurrency(normCur);
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    public VaultTransaction recordWithdrawal(BigDecimal amount, String customerName, String accountNumber, String note) {
        return recordWithdrawal(amount, amount, "EUR", customerName, accountNumber, note);
    }

    @Override
    @Transactional
    public VaultTransaction recordWithdrawal(BigDecimal eurAmount, BigDecimal originalAmount, String currency, String customerName, String accountNumber, String note) {
        if (eurAmount == null || eurAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Çıxarış məbləği müsbət olmalıdır");
        }

        String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
        BankVault vault = getOrCreateVault();
        vault.setBalance(vault.getBalance().subtract(eurAmount));
        bankVaultRepository.save(vault);

        String source = "Müştəri Çıxarışı • " + (customerName != null && !customerName.trim().isEmpty() ? customerName.trim() : "Müştəri")
                + (accountNumber != null && !accountNumber.trim().isEmpty() ? " (" + accountNumber.trim() + ")" : "");
        String memo = (note != null && !note.trim().isEmpty()) ? note.trim() : "Bankdan vəsait çıxarıldı";

        VaultTransaction vt = new VaultTransaction(
                eurAmount,
                "OUTFLOW",
                source,
                "CUSTOMER_WITHDRAWAL",
                memo,
                vault.getBalance()
        );
        vt.setOriginalAmount(originalAmount != null ? originalAmount : eurAmount);
        vt.setCurrency(normCur);
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    public VaultTransaction recordTransferOut(BigDecimal amount, BigDecimal commissionFee, String senderName, String senderAccount, String recipientName, String recipientAccount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Köçürmə məbləği müsbət olmalıdır");
        }

        BigDecimal fee = (commissionFee != null && commissionFee.compareTo(BigDecimal.ZERO) >= 0) ? commissionFee : BigDecimal.ZERO;
        BankVault vault = getOrCreateVault();

        vault.setBalance(vault.getBalance().subtract(amount));
        bankVaultRepository.save(vault);

        String source = "Pul Köçürməsi • " + (senderName != null ? senderName : senderAccount) + " -> " + (recipientName != null ? recipientName : recipientAccount);
        String memo = "Köçürülən: " + amount + " ₼"
                + (fee.compareTo(BigDecimal.ZERO) > 0 ? " (Bank komissiyası: +" + fee + " ₼)" : "")
                + (note != null && !note.trim().isEmpty() ? " | " + note.trim() : "");

        VaultTransaction vt = new VaultTransaction(
                amount,
                "OUTFLOW",
                source,
                "CUSTOMER_TRANSFER",
                memo,
                vault.getBalance()
        );
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    public VaultTransaction recordCommissionRevenue(BigDecimal commissionAmount, BigDecimal grossAmount, String senderInfo, String recipientInfo) {
        if (commissionAmount == null || commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Komissiya məbləği müsbət olmalıdır");
        }

        BankVault vault = getOrCreateVault();
        vault.setBalance(vault.getBalance().add(commissionAmount));
        bankVaultRepository.save(vault);

        String source = "Daxili Köçürmə Komissiyası • " + senderInfo + " -> " + recipientInfo;
        String note = "Komissiya gəliri: " + commissionAmount + " ₼ (Ümumi: " + grossAmount + " ₼)";

        VaultTransaction vt = new VaultTransaction(
                commissionAmount,
                "INFLOW",
                source,
                "TRANSFER_COMMISSION_REVENUE",
                note,
                vault.getBalance()
        );
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal amount, BigDecimal interestRate, String note) {
        return issueCustomerCredit(accountNumber, amount, "EUR", interestRate, note);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal inputAmount, String inputCurrency, BigDecimal interestRate, String note) {
        if (inputAmount == null || inputAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Kredit məbləği müsbət olmalıdır");
        }

        String normCur = com.financeapp.service.CurrencyConverter.normalize(inputCurrency);
        BigDecimal vaultEurAmount = com.financeapp.service.CurrencyConverter.convert(inputAmount, normCur, "EUR");

        BigDecimal rate = (interestRate != null && interestRate.compareTo(BigDecimal.ZERO) >= 0)
                ? interestRate : new BigDecimal("12.0");

        BigDecimal interestAmount = inputAmount.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = inputAmount.add(interestAmount);

        BankVault vault = getOrCreateVault();
        if (vault.getBalance().compareTo(vaultEurAmount) < 0) {
            throw new InsufficientBalanceException("Bank büdcəsində yetərli likvidlik yoxdur! Cari büdcə: €" + vault.getBalance());
        }

        String cleanAcc = accountNumber != null ? accountNumber.trim() : "";
        Account account = accountRepository.findByAccountNumber(cleanAcc)
                .or(() -> accountRepository.findByAccountNumber(cleanAcc.replaceAll("\\s+", "")))
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı: " + accountNumber));

        String oldCur = account.getCurrency() != null && !account.getCurrency().trim().isEmpty()
                ? com.financeapp.service.CurrencyConverter.normalize(account.getCurrency()) : normCur;
        BigDecimal currentBal = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        if (!oldCur.equals(normCur) && currentBal.compareTo(BigDecimal.ZERO) > 0) {
            currentBal = com.financeapp.service.CurrencyConverter.convert(currentBal, oldCur, normCur);
        }

        vault.setBalance(vault.getBalance().subtract(vaultEurAmount));
        bankVaultRepository.save(vault);

        // Xəzinədardan hansı valyuta seçilibsə (AZN, USD, EUR), hesab həmin valyuta ilə mədaxil olunur
        account.setCurrency(normCur);
        account.setBalance(currentBal.add(inputAmount));
        accountRepository.saveAndFlush(account);
        syncEmployeeBalance(account);

        String customerName = account.getUser() != null ? account.getUser().getUsername() : account.getAccountNumber();
        String memo = String.format("Bank Krediti • Təsdiqləndi (%s %s, Faiz: %s%%, Faiz Məbləği: %s %s, Geri Ödəniş: %s %s)%s",
                inputAmount, normCur,
                rate, interestAmount, normCur, totalRepayment, normCur,
                (note != null && !note.trim().isEmpty() ? " - " + note.trim() : ""));
        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-CRD";
        Transaction customerTx = new Transaction(
                inputAmount,
                memo,
                "DEPOSIT",
                ref,
                account,
                account.getUser()
        );
        customerTx.setCurrency(normCur);
        transactionRepository.save(customerTx);

        String vaultNote = String.format("Kredit: %s %s, Faiz: %s%% (+%s %s), Toplam: %s %s%s",
                inputAmount, normCur, rate, interestAmount, normCur, totalRepayment, normCur,
                (note != null && !note.trim().isEmpty() ? " | Qeyd: " + note.trim() : ""));

        VaultTransaction vt = new VaultTransaction(
                vaultEurAmount,
                "OUTFLOW",
                "Müştəri Krediti • " + customerName + " (" + account.getAccountNumber() + ")",
                "CUSTOMER_CREDIT",
                vaultNote,
                vault.getBalance()
        );
        vt.setOriginalAmount(inputAmount);
        vt.setCurrency(normCur);
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional
    public VaultTransaction issueCustomerCredit(String accountNumber, BigDecimal amount, String note) {
        return issueCustomerCredit(accountNumber, amount, new BigDecimal("12.0"), note);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public VaultTransaction transferToCustomer(String accountNumber, BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Köçürmə məbləği müsbət olmalıdır");
        }

        BankVault vault = getOrCreateVault();
        if (vault.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Bank büdcəsində yetərli likvidlik yoxdur! Cari büdcə: €" + vault.getBalance());
        }

        String cleanAcc = accountNumber != null ? accountNumber.trim() : "";
        Account account = accountRepository.findByAccountNumber(cleanAcc)
                .or(() -> accountRepository.findByAccountNumber(cleanAcc.replaceAll("\\s+", "")))
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı: " + accountNumber));

        vault.setBalance(vault.getBalance().subtract(amount));
        bankVaultRepository.save(vault);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.saveAndFlush(account);
        syncEmployeeBalance(account);

        String customerName = account.getUser() != null ? account.getUser().getUsername() : account.getAccountNumber();
        String memo = "Bank Köçürməsi" + (note != null && !note.trim().isEmpty() ? " (" + note.trim() + ")" : "");
        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-TRF";
        Transaction customerTx = new Transaction(
                amount,
                memo,
                "DEPOSIT",
                ref,
                account,
                account.getUser()
        );
        transactionRepository.save(customerTx);

        VaultTransaction vt = new VaultTransaction(
                amount,
                "OUTFLOW",
                "Müştəriyə Köçürmə • " + customerName + " (" + account.getAccountNumber() + ")",
                "CUSTOMER_TRANSFER",
                note != null && !note.trim().isEmpty() ? note.trim() : "Bank tərəfindən müştəriyə köçürmə",
                vault.getBalance()
        );
        return vaultTransactionRepository.save(vt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VaultTransaction> getAllTransactions() {
        return vaultTransactionRepository.findAllByOrderByTimestampDesc();
    }

    private void syncEmployeeBalance(Account account) {
        if (account == null || account.getUser() == null || account.getUser().getUsername() == null) {
            return;
        }
        bankEmployeeRepository.findByUsername(account.getUser().getUsername()).ifPresent(emp -> {
            emp.setAccountNumber(account.getAccountNumber());
            emp.setBalance(account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO);
            bankEmployeeRepository.saveAndFlush(emp);
        });
    }
}
