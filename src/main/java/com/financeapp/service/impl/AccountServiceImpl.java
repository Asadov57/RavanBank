package com.financeapp.service.impl;

import com.financeapp.dto.request.AccountCreateRequest;
import com.financeapp.dto.request.AccountUpdateRequest;
import com.financeapp.dto.request.DepositRequest;
import com.financeapp.dto.request.TransferRequest;
import com.financeapp.dto.request.WithdrawRequest;
import com.financeapp.dto.response.AccountResponse;
import com.financeapp.dto.response.TransferResponse;
import com.financeapp.enums.AccountStatus;
import com.financeapp.exception.InsufficientBalanceException;
import com.financeapp.mapper.AccountMapper;
import com.financeapp.model.Account;
import com.financeapp.model.User;
import com.financeapp.repository.AccountRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.AccountService;
import com.financeapp.service.BankVaultService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankVaultService bankVaultService;

    public AccountServiceImpl(AccountRepository accountRepository,
                               AccountMapper accountMapper,
                               UserRepository userRepository,
                               TransactionRepository transactionRepository,
                               PasswordEncoder passwordEncoder,
                               BankVaultService bankVaultService) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.bankVaultService = bankVaultService;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"accounts", "adminAccounts"}, allEntries = true)
    public AccountResponse createAccount(AccountCreateRequest request) {
        User user;
        String desiredUsername = (request.getUsername() != null && !request.getUsername().trim().isEmpty())
                ? request.getUsername().trim() : null;
        String desiredPassword = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                ? request.getPassword().trim() : "Password123!";
        String customerName = (request.getCustomerName() != null && !request.getCustomerName().trim().isEmpty())
                ? request.getCustomerName().trim() : null;

        if (desiredUsername != null) {
            if (userRepository.existsByUsername(desiredUsername)) {
                throw new IllegalArgumentException("Bu istifadəçi adı artıq mövcuddur: " + desiredUsername);
            }
            User newUser = new User();
            newUser.setUsername(desiredUsername);
            newUser.setPassword(passwordEncoder.encode(desiredPassword));
            newUser.setRawPassword(desiredPassword);
            newUser.setRole("ROLE_USER");
            newUser.setFullName(customerName != null ? customerName : desiredUsername);
            user = userRepository.save(newUser);
        } else if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("İstifadəçi tapılmadı: " + request.getUserId()));
        } else if (customerName != null) {
            String fallbackUsername = customerName.toLowerCase().replaceAll("\\s+", "_");
            if (userRepository.existsByUsername(fallbackUsername)) {
                fallbackUsername = fallbackUsername + "_" + UUID.randomUUID().toString().substring(0, 4);
            }
            User newUser = new User();
            newUser.setUsername(fallbackUsername);
            newUser.setPassword(passwordEncoder.encode(desiredPassword));
            newUser.setRawPassword(desiredPassword);
            newUser.setRole("ROLE_USER");
            newUser.setFullName(customerName);
            user = userRepository.save(newUser);
        } else {
            throw new RuntimeException("İstifadəçi adı və ya müştəri adı qeyd edilməlidir");
        }

        Account account = new Account();
        account.setAccountNumber(generate16DigitCardNumber());
        account.setBalance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO);
        account.setCurrency(request.getCurrency() != null ? request.getCurrency() : "AZN");
        account.setStatus(AccountStatus.ACTIVE);
        account.setUser(user);
        Account savedAccount = accountRepository.save(account);

        if (savedAccount.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-INI";
            com.financeapp.model.Transaction tx = new com.financeapp.model.Transaction(
                    savedAccount.getBalance(), "Başlanğıc Balans",
                    "DEPOSIT", ref, savedAccount, user);
            transactionRepository.save(tx);
        }

        return accountMapper.toResponse(savedAccount);
    }

    public static String generate16DigitCardNumber() {
        java.util.Random rnd = new java.util.Random();
        int p3 = rnd.nextInt(10000);
        int p4 = rnd.nextInt(10000);
        return String.format("4098 7056 %04d %04d", p3, p4);
    }

    public static boolean isAdminUsername(String name) {
        if (name == null) return false;
        String normalized = name.trim().replaceAll("\\s+", "").toLowerCase();
        return normalized.equals("ravanasadov") || normalized.equals("ravan5") || normalized.equals("ravan_asadov")
                || name.trim().equalsIgnoreCase("Ravan Asadov");
    }

    private static String capDesc(String text) {
        if (text == null) return null;
        return text.length() > 255 ? text.substring(0, 252) + "..." : text;
    }

    private Optional<Account> findAccountFlexible(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Optional.empty();
        }
        String trimmed = query.trim();
        Optional<Account> acc = accountRepository.findByAccountNumber(trimmed);
        if (acc.isPresent()) return acc;

        String digitsOnly = trimmed.replaceAll("\\s+", "");
        acc = accountRepository.findByAccountNumber(digitsOnly);
        if (acc.isPresent()) return acc;

        if (digitsOnly.length() == 16) {
            String spaced = digitsOnly.substring(0, 4) + " " + digitsOnly.substring(4, 8) + " " + digitsOnly.substring(8, 12) + " " + digitsOnly.substring(12, 16);
            acc = accountRepository.findByAccountNumber(spaced);
            if (acc.isPresent()) return acc;
        }

        List<Account> byUsername = accountRepository.findByUserUsername(trimmed);
        if (!byUsername.isEmpty()) return Optional.of(byUsername.get(0));

        return Optional.empty();
    }

    @Override
    @Cacheable(value = "accounts", key = "#username")
    public List<AccountResponse> getAllAccounts(String username) {
        return userRepository.findFirstByUsername(username)
                .map(user -> accountRepository.findByUser(user).stream()
                        .map(accountMapper::toResponse)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private boolean isCustomerAccount(Account acc) {
        if (acc == null) return false;
        if ("SALARY".equalsIgnoreCase(acc.getAccountType())) return false;
        if (acc.getUser() != null && acc.getUser().getRole() != null) {
            String role = acc.getUser().getRole().toUpperCase();
            if (role.contains("EMPLOYEE") || role.contains("SECURITY") || role.contains("ADMIN")) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Cacheable(value = "adminAccounts")
    public List<AccountResponse> getAllAccountsForAdmin() {
        return accountRepository.findAll().stream()
                .filter(this::isCustomerAccount)
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "accountsByStatus", key = "#status.name()")
    public List<AccountResponse> getAccountsByStatus(AccountStatus status) {
        return accountRepository.findByStatus(status).stream()
                .filter(this::isCustomerAccount)
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "accountByNumber", key = "#accountNumber")
    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        Account account = findAccountFlexible(accountNumber)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı: " + accountNumber));
        return accountMapper.toResponse(account);
    }

    @Override
    @Cacheable(value = "accountById", key = "#id")
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + id));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", key = "#id"),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public AccountResponse updateAccount(Long id, AccountUpdateRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + id));

        account.setCurrency(request.getCurrency());
        account.setStatus(request.getStatus());

        Account updated = accountRepository.save(account);
        return accountMapper.toResponse(updated);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", key = "#id"),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + id));
        transactionRepository.deleteAll(
                transactionRepository.findByAccountOrderByTimestampDesc(account));
        accountRepository.delete(account);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true)
    })
    public AccountResponse deposit(DepositRequest request) {
        if (request == null || request.getAccountNumber() == null || request.getAccountNumber().trim().isEmpty()
                || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Məlumatlar tam daxil edilməyib");
        }

        String accQuery = request.getAccountNumber().trim();
        Account account = findAccountFlexible(accQuery)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı: " + accQuery));

        account.setBalance(account.getBalance().add(request.getAmount()));
        Account savedAccount = accountRepository.save(account);

        String memo = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? request.getDescription().trim() : "Mədaxil / Depozit";
        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-DEP";
        com.financeapp.model.Transaction tx = new com.financeapp.model.Transaction(
                request.getAmount(), memo, "DEPOSIT", ref, savedAccount, savedAccount.getUser());
        transactionRepository.save(tx);

        String customerName = savedAccount.getUser() != null
                ? (savedAccount.getUser().getFullName() != null && !savedAccount.getUser().getFullName().trim().isEmpty()
                        ? savedAccount.getUser().getFullName() : savedAccount.getUser().getUsername())
                : savedAccount.getAccountNumber();
        bankVaultService.recordDeposit(request.getAmount(), customerName, savedAccount.getAccountNumber(), memo);

        return accountMapper.toResponse(savedAccount);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true)
    })
    public AccountResponse withdraw(WithdrawRequest request) {
        Account account = findAccountFlexible(request.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı"));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            String accSym = com.financeapp.service.CurrencyConverter.symbol(account.getCurrency());
            throw new InsufficientBalanceException("Kifayət qədər balans yoxdur / Insufficient balance! Tələb: "
                    + request.getAmount() + " " + accSym + ", Mövcud: " + account.getBalance() + " " + accSym);
        }
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        Account savedAccount = accountRepository.save(account);

        String memo = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? request.getDescription().trim() : "Xərcləmə / Çıxarış";
        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-WTH";
        com.financeapp.model.Transaction tx = new com.financeapp.model.Transaction(
                request.getAmount(), memo, "WITHDRAWAL", ref, savedAccount, savedAccount.getUser());
        transactionRepository.save(tx);

        return accountMapper.toResponse(savedAccount);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true)
    })
    public TransferResponse transfer(TransferRequest request, String authenticatedUsername) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Köçürmə məbləği müsbət olmalıdır");
        }

        Account senderAccount;
        if (request.getSenderAccountNumber() != null && !request.getSenderAccountNumber().trim().isEmpty()) {
            senderAccount = findAccountFlexible(request.getSenderAccountNumber().trim())
                    .orElseThrow(() -> new RuntimeException("Göndərən hesab tapılmadı: " + request.getSenderAccountNumber()));
        } else {
            List<Account> userAccounts = accountRepository.findByUserUsername(authenticatedUsername);
            if (userAccounts.isEmpty()) throw new RuntimeException("Göndərənin heç bir aktiv hesabı tapılmadı");
            senderAccount = userAccounts.get(0);
        }

        if (senderAccount.getUser() != null && !senderAccount.getUser().getUsername().equals(authenticatedUsername)) {
            throw new RuntimeException("Yalnız öz hesabınızdan köçürmə edə bilərsiniz");
        }
        if (senderAccount.getStatus() == AccountStatus.BLOCKED) {
            throw new RuntimeException("Hesabınız BLOKLANIB! Köçürmə aparmaq mümkün deyil.");
        }

        String recipientQuery = (request.getRecipientAccountNumber() != null)
                ? request.getRecipientAccountNumber().trim() : "";
        if (recipientQuery.isEmpty()) {
            throw new IllegalArgumentException("Qəbuledici hesab nömrəsi qeyd edilməlidir");
        }

        Account recipientAccount = findAccountFlexible(recipientQuery)
                .orElseThrow(() -> new RuntimeException("Qəbuledici tapılmadı: " + recipientQuery));

        if (recipientAccount.getStatus() == AccountStatus.BLOCKED) {
            throw new RuntimeException("Qəbuledici hesab hazırda BLOKLANIB.");
        }
        if (senderAccount.getId().equals(recipientAccount.getId())) {
            throw new IllegalArgumentException("Öz hesabınıza köçürmə edə bilməzsiniz");
        }

        String senderCurrency = com.financeapp.service.CurrencyConverter.normalize(senderAccount.getCurrency());
        String recipientCurrency = com.financeapp.service.CurrencyConverter.normalize(recipientAccount.getCurrency());
        String transferCurrency = (request.getCurrency() != null && !request.getCurrency().trim().isEmpty())
                ? com.financeapp.service.CurrencyConverter.normalize(request.getCurrency())
                : senderCurrency;

        BigDecimal grossAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal senderGross = com.financeapp.service.CurrencyConverter.convert(grossAmount, transferCurrency, senderCurrency);
        BigDecimal recipientCredited = com.financeapp.service.CurrencyConverter.convert(grossAmount, transferCurrency, recipientCurrency);

        BigDecimal commissionFee;
        if (senderGross.compareTo(new BigDecimal("10.00")) <= 0) {
            commissionFee = new BigDecimal("0.02");
        } else if (senderGross.compareTo(new BigDecimal("100.00")) <= 0) {
            commissionFee = new BigDecimal("0.05");
        } else {
            commissionFee = new BigDecimal("0.10");
        }
        BigDecimal totalDeducted = senderGross.add(commissionFee);

        String senderSym = com.financeapp.service.CurrencyConverter.symbol(senderCurrency);
        String transferSym = com.financeapp.service.CurrencyConverter.symbol(transferCurrency);
        String recipientSym = com.financeapp.service.CurrencyConverter.symbol(recipientCurrency);

        if (senderAccount.getBalance().compareTo(totalDeducted) < 0) {
            throw new InsufficientBalanceException("Kifayət qədər balans yoxdur / Insufficient balance! Tələb: " + totalDeducted
                    + " " + senderSym + " (köçürmə: " + senderGross + " " + senderSym + " + komissiya: " + commissionFee
                    + " " + senderSym + "), Mövcud: " + senderAccount.getBalance() + " " + senderSym);
        }

        senderAccount.setBalance(senderAccount.getBalance().subtract(totalDeducted));
        accountRepository.save(senderAccount);
        recipientAccount.setBalance(recipientAccount.getBalance().add(recipientCredited));
        accountRepository.save(recipientAccount);

        String senderName = senderAccount.getUser() != null
                ? (senderAccount.getUser().getFullName() != null && !senderAccount.getUser().getFullName().trim().isEmpty()
                        ? senderAccount.getUser().getFullName() : senderAccount.getUser().getUsername())
                : senderAccount.getAccountNumber();
        String recipientName = recipientAccount.getUser() != null
                ? (recipientAccount.getUser().getFullName() != null && !recipientAccount.getUser().getFullName().trim().isEmpty()
                        ? recipientAccount.getUser().getFullName() : recipientAccount.getUser().getUsername())
                : recipientAccount.getAccountNumber();

        String txRef = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-TRF";
        String customNote = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? " (" + request.getDescription().trim() + ")" : "";

        if (commissionFee.compareTo(BigDecimal.ZERO) > 0) {
            bankVaultService.recordCommissionRevenue(commissionFee, senderGross,
                    senderName + " (" + senderAccount.getAccountNumber() + ")",
                    recipientName + " (" + recipientAccount.getAccountNumber() + ")");
        }

        String fxSenderInfo = (!transferCurrency.equals(senderCurrency))
                ? " | Göndərilən: " + grossAmount + " " + transferCurrency + " → Çıxılan: " + senderGross + " " + senderCurrency
                : "";
        String fxRecipientInfo = (!transferCurrency.equals(recipientCurrency))
                ? " | Göndərilən: " + grossAmount + " " + transferCurrency + " → Çevrilən: " + recipientCredited + " " + recipientCurrency
                : "";

        com.financeapp.model.Transaction senderTx = new com.financeapp.model.Transaction(
                totalDeducted,
                "Daxili Köçürmə: " + recipientName + " (" + recipientAccount.getAccountNumber() + ") • "
                        + grossAmount + " " + transferSym + fxSenderInfo + " • Komissiya: " + commissionFee + " " + senderSym + customNote,
                "WITHDRAWAL", txRef, senderAccount, senderAccount.getUser());
        senderTx.setDescription(capDesc(senderTx.getDescription()));
        transactionRepository.save(senderTx);

        com.financeapp.model.Transaction recipientTx = new com.financeapp.model.Transaction(
                recipientCredited,
                "Mədaxil Köçürmə: " + senderName + " (" + senderAccount.getAccountNumber() + ") tərəfindən • "
                        + grossAmount + " " + transferSym + fxRecipientInfo + customNote,
                "DEPOSIT", txRef, recipientAccount, recipientAccount.getUser());
        recipientTx.setDescription(capDesc(recipientTx.getDescription()));
        transactionRepository.save(recipientTx);

        TransferResponse response = new TransferResponse();
        response.setSenderAccountNumber(senderAccount.getAccountNumber());
        response.setRecipientAccountNumber(recipientAccount.getAccountNumber());
        response.setRecipientName(recipientName);
        response.setGrossAmount(grossAmount);
        response.setCommissionFee(commissionFee);
        response.setNetAmount(recipientCredited);
        response.setTotalDeducted(totalDeducted);
        response.setSentCurrency(transferCurrency);
        response.setRecipientCurrency(recipientCurrency);
        response.setConvertedAmount(recipientCredited);
        response.setReferenceNumber(txRef);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountByNumber", allEntries = true),
            @CacheEvict(value = "accounts", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true)
    })
    public TransferResponse corporateTransfer(TransferRequest request, String authenticatedUsername, String companyName) {
        String recipientQuery = (request.getRecipientAccountNumber() != null)
                ? request.getRecipientAccountNumber().trim() : "";
        String cleanCompany = (companyName != null && !companyName.trim().isEmpty())
                ? companyName.trim()
                : recipientQuery;
        if (cleanCompany.isEmpty()) {
            throw new IllegalArgumentException("Şirkət adı daxil edilməlidir");
        }
        String effectiveCorpTarget = !recipientQuery.isEmpty() ? recipientQuery : cleanCompany;

        // Yalnız açıq daxili hesab nömrəsi verildikdə daxili köçürməyə yönləndir
        if (!recipientQuery.isEmpty()
                && !recipientQuery.equalsIgnoreCase(cleanCompany)
                && findAccountFlexible(recipientQuery).isPresent()) {
            return transfer(request, authenticatedUsername);
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Köçürmə məbləği müsbət olmalıdır");
        }

        Account senderAccount;
        if (request.getSenderAccountNumber() != null && !request.getSenderAccountNumber().trim().isEmpty()) {
            senderAccount = findAccountFlexible(request.getSenderAccountNumber().trim())
                    .orElseThrow(() -> new RuntimeException("Göndərən hesab tapılmadı: " + request.getSenderAccountNumber()));
        } else {
            List<Account> userAccounts = accountRepository.findByUserUsername(authenticatedUsername);
            if (userAccounts.isEmpty()) throw new RuntimeException("Göndərənin heç bir aktiv hesabı tapılmadı");
            senderAccount = userAccounts.get(0);
        }
        if (senderAccount.getUser() != null && !senderAccount.getUser().getUsername().equals(authenticatedUsername)) {
            throw new RuntimeException("Yalnız öz hesabınızdan köçürmə edə bilərsiniz");
        }
        if (senderAccount.getStatus() == AccountStatus.BLOCKED) {
            throw new RuntimeException("Hesabınız BLOKLANIB! Köçürmə aparmaq mümkün deyil.");
        }

        String senderCurrency = com.financeapp.service.CurrencyConverter.normalize(senderAccount.getCurrency());
        String transferCurrency = (request.getCurrency() != null && !request.getCurrency().trim().isEmpty())
                ? com.financeapp.service.CurrencyConverter.normalize(request.getCurrency())
                : senderCurrency;

        BigDecimal grossAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal senderGross = com.financeapp.service.CurrencyConverter.convert(grossAmount, transferCurrency, senderCurrency);

        BigDecimal commissionFee;
        if (senderGross.compareTo(new BigDecimal("10.00")) <= 0) {
            commissionFee = new BigDecimal("0.02");
        } else if (senderGross.compareTo(new BigDecimal("100.00")) <= 0) {
            commissionFee = new BigDecimal("0.05");
        } else {
            commissionFee = new BigDecimal("0.10");
        }
        BigDecimal totalDeducted = senderGross.add(commissionFee);
        String senderSym = com.financeapp.service.CurrencyConverter.symbol(senderCurrency);
        String transferSym = com.financeapp.service.CurrencyConverter.symbol(transferCurrency);

        if (senderAccount.getBalance().compareTo(totalDeducted) < 0) {
            throw new InsufficientBalanceException("Kifayət qədər balans yoxdur / Insufficient balance! Tələb: " + totalDeducted
                    + " " + senderSym + " (köçürmə: " + senderGross + " " + senderSym + " + komissiya: " + commissionFee
                    + " " + senderSym + "), Mövcud: " + senderAccount.getBalance() + " " + senderSym);
        }

        senderAccount.setBalance(senderAccount.getBalance().subtract(totalDeducted));
        accountRepository.save(senderAccount);

        String senderName = senderAccount.getUser() != null
                ? (senderAccount.getUser().getFullName() != null && !senderAccount.getUser().getFullName().trim().isEmpty()
                        ? senderAccount.getUser().getFullName() : senderAccount.getUser().getUsername())
                : senderAccount.getAccountNumber();
        String corpLabel = effectiveCorpTarget.equalsIgnoreCase(cleanCompany)
                ? cleanCompany
                : cleanCompany + " (" + effectiveCorpTarget + ")";

        if (commissionFee.compareTo(BigDecimal.ZERO) > 0) {
            bankVaultService.recordCommissionRevenue(commissionFee, senderGross,
                    senderName + " (" + senderAccount.getAccountNumber() + ")",
                    corpLabel);
        }

        String txRef = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-CRP";
        String customNote = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? " (" + request.getDescription().trim() + ")" : "";
        String fxInfo = (!transferCurrency.equals(senderCurrency))
                ? " | Göndərilən: " + grossAmount + " " + transferCurrency + " → Çıxılan: " + senderGross + " " + senderCurrency
                : "";

        com.financeapp.model.Transaction senderTx = new com.financeapp.model.Transaction(
                totalDeducted,
                "Korporativ Köçürmə: " + corpLabel + " • "
                        + grossAmount + " " + transferSym + fxInfo + " • Komissiya: " + commissionFee + " " + senderSym + customNote,
                "WITHDRAWAL", txRef, senderAccount, senderAccount.getUser());
        senderTx.setDescription(capDesc(senderTx.getDescription()));
        transactionRepository.save(senderTx);

        TransferResponse response = new TransferResponse();
        response.setSenderAccountNumber(senderAccount.getAccountNumber());
        response.setRecipientAccountNumber(effectiveCorpTarget);
        response.setRecipientName(cleanCompany);
        response.setGrossAmount(grossAmount);
        response.setCommissionFee(commissionFee);
        response.setNetAmount(grossAmount);
        response.setTotalDeducted(totalDeducted);
        response.setSentCurrency(transferCurrency);
        response.setRecipientCurrency(transferCurrency);
        response.setConvertedAmount(grossAmount);
        response.setReferenceNumber(txRef);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true)
    })
    public void blockAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + id));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountById", allEntries = true),
            @CacheEvict(value = "accountsByStatus", allEntries = true),
            @CacheEvict(value = "adminAccounts", allEntries = true)
    })
    public void unblockAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + id));
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
    }

    @Override
    public long countByStatus(AccountStatus status) {
        return accountRepository.countByStatus(status);
    }
}
