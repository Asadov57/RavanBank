package com.financeapp.config;

import com.financeapp.model.BankVault;
import com.financeapp.model.User;
import com.financeapp.model.VaultTransaction;
import com.financeapp.repository.AccountRepository;
import com.financeapp.repository.BankVaultRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.repository.VaultTransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionRepository transactionRepository;
    private final BankVaultRepository bankVaultRepository;
    private final VaultTransactionRepository vaultTransactionRepository;
    private final com.financeapp.repository.InquiryAdminConfigRepository inquiryAdminConfigRepository;
    private final com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository;

    public DataInitializer(UserRepository userRepository,
                           AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder,
                           TransactionRepository transactionRepository,
                           BankVaultRepository bankVaultRepository,
                           VaultTransactionRepository vaultTransactionRepository,
                           com.financeapp.repository.InquiryAdminConfigRepository inquiryAdminConfigRepository,
                           com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionRepository = transactionRepository;
        this.bankVaultRepository = bankVaultRepository;
        this.vaultTransactionRepository = vaultTransactionRepository;
        this.inquiryAdminConfigRepository = inquiryAdminConfigRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
    }

    @Override
    public void run(String... args) {
        Optional<User> adminOpt = userRepository.findFirstByUsernameIgnoreCase("Ravan Asadov");
        User admin = adminOpt.orElseGet(User::new);
        admin.setUsername("Ravan Asadov");
        admin.setPassword(passwordEncoder.encode("Ravan 2005"));
        admin.setRole("ROLE_ADMIN");
        admin.setFullName("Ravan Asadov");
        admin.setEmail("rvans6754@gmail.com");
        userRepository.save(admin);

        BigDecimal initialVaultBudget = new BigDecimal("10000000000.00");
        BankVault existingVault = bankVaultRepository.findFirstByOrderByIdAsc().orElse(null);
        if (existingVault == null) {
            BankVault vault = new BankVault(initialVaultBudget, "EUR");
            bankVaultRepository.save(vault);

            vaultTransactionRepository.save(new VaultTransaction(
                    initialVaultBudget,
                    "INFLOW",
                    "Bank Nizamnamə Kapitalı",
                    "CAPITAL_DEPOSIT",
                    "İlkin bank büdcəsi (10 Milyard EUR)",
                    initialVaultBudget
            ));
        } else {

            if (!"EUR".equalsIgnoreCase(existingVault.getCurrency()) || existingVault.getBalance().compareTo(new BigDecimal("100000000.00")) < 0) {
                existingVault.setBalance(initialVaultBudget);
                existingVault.setCurrency("EUR");
                bankVaultRepository.save(existingVault);

                vaultTransactionRepository.save(new VaultTransaction(
                        initialVaultBudget,
                        "INFLOW",
                        "Nizamnamə Kapitalının Artırılması",
                        "CAPITAL_DEPOSIT",
                        "Bank büdcəsinin 10 Milyard EUR-ya artırılması",
                        initialVaultBudget
                ));
            }
        }

        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && u.getRole().toUpperCase().contains("ADMIN"))
                .forEach(adminUser -> {
                    accountRepository.findByUser(adminUser).forEach(acc -> {
                        transactionRepository.deleteAll(transactionRepository.findByAccountOrderByTimestampDesc(acc));
                        accountRepository.delete(acc);
                    });
                    bankEmployeeRepository.findByUsername(adminUser.getUsername())
                            .ifPresent(bankEmployeeRepository::delete);
                });

        if (inquiryAdminConfigRepository.findFirstByOrderByIdAsc().isEmpty()) {
            com.financeapp.model.InquiryAdminConfig config = new com.financeapp.model.InquiryAdminConfig(
                    passwordEncoder.encode("RavanSorgu")
            );
            inquiryAdminConfigRepository.save(config);
        }
    }
}
