package com.financeapp.controller;

import com.financeapp.dto.request.DepositRequest;
import com.financeapp.dto.request.WithdrawRequest;
import com.financeapp.dto.response.AccountResponse;
import com.financeapp.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Controller
public class DashboardController {

    private final AccountService accountService;
    private final com.financeapp.repository.UserRepository userRepository;
    private final com.financeapp.repository.AccountRepository accountRepository;
    private final com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository;
    private final com.financeapp.repository.TransactionRepository transactionRepository;
    private final com.financeapp.repository.CustomerMessageRepository customerMessageRepository;
    private final com.financeapp.repository.SecurityAppealRepository securityAppealRepository;
    private final com.financeapp.repository.LoanApplicationRepository loanApplicationRepository;

    public DashboardController(AccountService accountService,
                               com.financeapp.repository.UserRepository userRepository,
                               com.financeapp.repository.AccountRepository accountRepository,
                               com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository,
                               com.financeapp.repository.TransactionRepository transactionRepository,
                               com.financeapp.repository.CustomerMessageRepository customerMessageRepository,
                               com.financeapp.repository.SecurityAppealRepository securityAppealRepository,
                               com.financeapp.repository.LoanApplicationRepository loanApplicationRepository) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.transactionRepository = transactionRepository;
        this.customerMessageRepository = customerMessageRepository;
        this.securityAppealRepository = securityAppealRepository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    @GetMapping("/dashboard")
    public String showDashboard(
            Model model,
            Authentication authentication,
            @RequestParam(required = false) String successMessage,
            @RequestParam(required = false) String errorMessage) {

        String username = authentication != null ? authentication.getName() : "";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
        if (isAdmin) {
            return "redirect:/admin";
        }
        boolean isEmployee = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                        || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                        || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));
        if (isEmployee) {
            return "redirect:/employee/portal";
        }

        com.financeapp.model.User user = userRepository.findFirstByUsernameIgnoreCase(username)
                .or(() -> userRepository.findFirstByEmailIgnoreCase(username))
                .orElse(null);

        String canonicalUsername = user != null ? user.getUsername() : username;
        String userEmail = user != null && user.getEmail() != null ? user.getEmail() : "";

        if (user != null && user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
            model.addAttribute("customerName", user.getFullName());
        } else {
            model.addAttribute("customerName", canonicalUsername);
        }
        if (user != null) {
            if (user.getPhoneNumber() != null) {
                model.addAttribute("phoneNumber", user.getPhoneNumber());
            }
            if (user.getIdCardSerial() != null) {
                model.addAttribute("idCardSerial", user.getIdCardSerial());
            }
            if (user.getFinCode() != null) {
                model.addAttribute("finCode", user.getFinCode());
            }
        }

        List<AccountResponse> accounts = accountService.getAllAccounts(canonicalUsername);
        com.financeapp.model.Account resolvedDashAccount = resolveOrCreatePrimaryAccount(user, java.util.Optional.empty());

        String dashCurrency = "AZN";
        if (!accounts.isEmpty()) {
            AccountResponse primary = accounts.get(0);
            if (resolvedDashAccount != null) {
                if (resolvedDashAccount.getBalance() != null) {
                    primary.setBalance(resolvedDashAccount.getBalance());
                }
                if (resolvedDashAccount.getCurrency() != null && !resolvedDashAccount.getCurrency().isBlank()) {
                    primary.setCurrency(resolvedDashAccount.getCurrency());
                }
            }
            if (primary.getCurrency() != null && !primary.getCurrency().isBlank()) {
                dashCurrency = com.financeapp.service.CurrencyConverter.normalize(primary.getCurrency());
            }
            model.addAttribute("account", primary);
        } else if (resolvedDashAccount != null) {
            dashCurrency = com.financeapp.service.CurrencyConverter.normalize(resolvedDashAccount.getCurrency());
        }
        model.addAttribute("accountCurrency", dashCurrency);
        model.addAttribute("accountCurrencySymbol", com.financeapp.service.CurrencyConverter.symbol(dashCurrency));
        model.addAttribute("customerId", "@" + canonicalUsername);

        model.addAttribute("accounts", accounts);

        List<com.financeapp.model.Transaction> transactions = loadMergedTransactions(user, resolvedDashAccount);
        BigDecimal monthlyDepositTotal = BigDecimal.ZERO;
        BigDecimal monthlyWithdrawTotal = BigDecimal.ZERO;

        for (com.financeapp.model.Transaction tx : transactions) {
            String txCur = tx.getCurrency() != null && !tx.getCurrency().isBlank() ? tx.getCurrency() : dashCurrency;
            BigDecimal converted = com.financeapp.service.CurrencyConverter.convert(tx.getAmount(), txCur, dashCurrency);
            if ("DEPOSIT".equalsIgnoreCase(tx.getType())) {
                monthlyDepositTotal = monthlyDepositTotal.add(converted);
            } else if ("WITHDRAWAL".equalsIgnoreCase(tx.getType())) {
                monthlyWithdrawTotal = monthlyWithdrawTotal.add(converted);
            }
        }

        model.addAttribute("transactions", transactions);
        model.addAttribute("monthlyDepositTotal", monthlyDepositTotal);
        model.addAttribute("monthlyWithdrawTotal", monthlyWithdrawTotal);

        String userFullName = user != null && user.getFullName() != null ? user.getFullName() : "";
        List<com.financeapp.model.CustomerMessage> customerMessages = customerMessageRepository.findMessagesForUser(canonicalUsername, userFullName, userEmail);
        model.addAttribute("customerMessages", customerMessages);

        List<com.financeapp.model.SecurityAppeal> myAppeals = securityAppealRepository.findAppealsForUser(canonicalUsername, userFullName);
        model.addAttribute("myAppeals", myAppeals != null ? myAppeals : Collections.emptyList());

        List<com.financeapp.model.LoanApplication> loanApplications = loanApplicationRepository.findByUsernameOrderByCreatedAtDesc(canonicalUsername);
        model.addAttribute("loanApplications", loanApplications != null ? loanApplications : Collections.emptyList());

        if (successMessage != null) model.addAttribute("successMessage", successMessage);
        if (errorMessage != null)   model.addAttribute("errorMessage", errorMessage);

        return "dashboard";
    }

    private com.financeapp.model.Account resolveOrCreatePrimaryAccount(
            com.financeapp.model.User user,
            java.util.Optional<com.financeapp.model.BankEmployee> empOpt) {
        if (user == null) {
            return null;
        }
        java.util.Optional<com.financeapp.model.Account> existingOpt = accountRepository.findFirstByUser(user);
        if (existingOpt.isPresent()) {
            com.financeapp.model.Account acc = existingOpt.get();
            if (empOpt != null && empOpt.isPresent()) {
                com.financeapp.model.BankEmployee emp = empOpt.get();
                emp.setAccountNumber(acc.getAccountNumber());
                emp.setBalance(acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO);
                bankEmployeeRepository.save(emp);
            }
            return acc;
        }

        com.financeapp.model.Account acc = new com.financeapp.model.Account();
        if (user != null && user.getRole() != null && user.getRole().toUpperCase().contains("ADMIN")) {
            return null;
        }
        if (empOpt != null && empOpt.isPresent()) {
            com.financeapp.model.BankEmployee emp = empOpt.get();
            acc.setAccountNumber(emp.getAccountNumber() != null && !emp.getAccountNumber().isEmpty()
                    ? emp.getAccountNumber()
                    : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
            acc.setBalance(emp.getBalance() != null ? emp.getBalance() : BigDecimal.ZERO);
            acc.setCurrency("EUR");
            acc.setAccountType("SALARY");
            acc.setStatus("ACTIVE".equalsIgnoreCase(emp.getAccountStatus())
                    ? com.financeapp.enums.AccountStatus.ACTIVE
                    : com.financeapp.enums.AccountStatus.PENDING_VERIFICATION);
            acc.setUser(user);
            com.financeapp.model.Account saved = accountRepository.save(acc);
            emp.setAccountNumber(saved.getAccountNumber());
            emp.setBalance(saved.getBalance() != null ? saved.getBalance() : BigDecimal.ZERO);
            bankEmployeeRepository.save(emp);
            return saved;
        } else {
            acc.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
            acc.setBalance(BigDecimal.ZERO);
            acc.setCurrency("USD");
            acc.setAccountType("CHECKING");
            acc.setStatus(com.financeapp.enums.AccountStatus.ACTIVE);
            acc.setUser(user);
            return accountRepository.save(acc);
        }
    }

    @GetMapping({"/card", "/account/card", "/employee/card"})
    public String showBankCardPage(
            @RequestParam(value = "username", required = false) String targetUsername,
            @RequestParam(required = false) String successMessage,
            @RequestParam(required = false) String errorMessage,
            jakarta.servlet.http.HttpServletRequest request,
            Model model,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String loggedInUsername = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
        boolean isEmployeeAuth = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                        || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                        || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));

        String lookupUsername = (targetUsername != null && !targetUsername.trim().isEmpty() && (isAdmin || isEmployeeAuth))
                ? targetUsername.trim()
                : loggedInUsername;

        com.financeapp.model.User user = userRepository.findFirstByUsernameIgnoreCase(lookupUsername)
                .or(() -> userRepository.findFirstByEmailIgnoreCase(lookupUsername))
                .orElse(null);

        if (user != null && user.getRole() != null && user.getRole().toUpperCase().contains("ADMIN")) {
            return "redirect:/admin";
        }
        if (isAdmin && (targetUsername == null || targetUsername.trim().isEmpty())) {
            return "redirect:/admin";
        }

        String canonicalUsername = user != null ? user.getUsername() : lookupUsername;
        String displayName = (user != null && user.getFullName() != null && !user.getFullName().trim().isEmpty())
                ? user.getFullName() : canonicalUsername;

        boolean isEmployee = isEmployeeAuth;
        java.util.Optional<com.financeapp.model.BankEmployee> empOpt = bankEmployeeRepository.findByUsername(canonicalUsername);
        if (empOpt.isPresent()) {
            isEmployee = true;
            com.financeapp.model.BankEmployee emp = empOpt.get();
            displayName = emp.getFullName() != null ? emp.getFullName() : displayName;
            model.addAttribute("employee", emp);
        }

        if (!isAdmin && !isEmployee) {
            return "redirect:/dashboard";
        }

        com.financeapp.model.Account resolvedAccount = resolveOrCreatePrimaryAccount(user, empOpt);

        List<AccountResponse> accounts = accountService.getAllAccounts(canonicalUsername);
        AccountResponse primary = null;
        if (!accounts.isEmpty()) {
            primary = accounts.get(0);
            if (resolvedAccount != null) {
                if (resolvedAccount.getBalance() != null) {
                    primary.setBalance(resolvedAccount.getBalance());
                }
                if (resolvedAccount.getCurrency() != null && !resolvedAccount.getCurrency().isBlank()) {
                    primary.setCurrency(resolvedAccount.getCurrency());
                }
            }
        } else if (resolvedAccount != null) {
            primary = new AccountResponse(resolvedAccount.getId(), resolvedAccount.getAccountNumber(),
                    resolvedAccount.getBalance(), resolvedAccount.getCurrency(), resolvedAccount.getStatus());
            primary.setAccountType(resolvedAccount.getAccountType());
            primary.setCustomerName(displayName);
            primary.setUsername(canonicalUsername);
        } else if (empOpt.isPresent()) {
            com.financeapp.model.BankEmployee emp = empOpt.get();
            String accNum = emp.getAccountNumber() != null && !emp.getAccountNumber().isEmpty()
                    ? emp.getAccountNumber()
                    : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber();
            primary = new AccountResponse(emp.getId(), accNum, emp.getBalance() != null ? emp.getBalance() : BigDecimal.ZERO, emp.getSalaryCurrency(), com.financeapp.enums.AccountStatus.ACTIVE);
            primary.setAccountType("CHECKING");
            primary.setCustomerName(displayName);
            primary.setUsername(canonicalUsername);
        }

        String cardCurrency = (primary != null && primary.getCurrency() != null && !primary.getCurrency().isBlank())
                ? com.financeapp.service.CurrencyConverter.normalize(primary.getCurrency())
                : "AZN";
        model.addAttribute("accountCurrency", cardCurrency);
        model.addAttribute("accountCurrencySymbol", com.financeapp.service.CurrencyConverter.symbol(cardCurrency));

        List<com.financeapp.model.Transaction> transactions = loadMergedTransactions(user, resolvedAccount);
        BigDecimal monthlyDepositTotal = BigDecimal.ZERO;
        BigDecimal monthlyWithdrawTotal = BigDecimal.ZERO;

        for (com.financeapp.model.Transaction tx : transactions) {
            String txCur = tx.getCurrency() != null && !tx.getCurrency().isBlank() ? tx.getCurrency() : cardCurrency;
            BigDecimal converted = com.financeapp.service.CurrencyConverter.convert(tx.getAmount(), txCur, cardCurrency);
            if ("DEPOSIT".equalsIgnoreCase(tx.getType())) {
                monthlyDepositTotal = monthlyDepositTotal.add(converted);
            } else if ("WITHDRAWAL".equalsIgnoreCase(tx.getType())) {
                monthlyWithdrawTotal = monthlyWithdrawTotal.add(converted);
            }
        }

        String userEmail = user != null && user.getEmail() != null ? user.getEmail() : "";
        String userFullName = user != null && user.getFullName() != null ? user.getFullName() : displayName;
        List<com.financeapp.model.CustomerMessage> customerMessages = customerMessageRepository.findMessagesForUser(canonicalUsername, userFullName, userEmail);
        List<com.financeapp.model.SecurityAppeal> myAppeals = securityAppealRepository.findAppealsForUser(canonicalUsername, userFullName);
        if (user != null && user.getPhoneNumber() != null) {
            model.addAttribute("phoneNumber", user.getPhoneNumber());
        } else if (empOpt.isPresent() && empOpt.get().getPhoneNumber() != null) {
            model.addAttribute("phoneNumber", empOpt.get().getPhoneNumber());
        }
        model.addAttribute("customerMessages", customerMessages != null ? customerMessages : Collections.emptyList());
        model.addAttribute("myAppeals", myAppeals != null ? myAppeals : Collections.emptyList());

        String backUrl = isAdmin ? "/admin" : "/employee/portal";
        String backLabel = isAdmin ? "Admin Panelə Qayıt" : "İşçi Portalına Qayıt";
        String referer = request != null ? request.getHeader("Referer") : null;
        if (referer != null && !referer.contains("/card")) {
            if (referer.contains("/admin/security")) {
                backUrl = "/admin/security";
                backLabel = "Təhlükəsizlik Panelinə Qayıt";
            } else if (referer.contains("/admin/inquiry")) {
                backUrl = "/admin/inquiry";
                backLabel = "Sorğu Panelinə Qayıt";
            } else if (referer.contains("/admin/treasury")) {
                backUrl = "/admin/treasury";
                backLabel = "Xəzinədar Panelinə Qayıt";
            } else if (referer.contains("/employee/portal")) {
                backUrl = "/employee/portal";
                backLabel = "İşçi Portalına Qayıt";
            } else if (referer.contains("/admin")) {
                backUrl = "/admin";
                backLabel = "Admin Panelə Qayıt";
            }
        }

        model.addAttribute("account", primary);
        model.addAttribute("customerName", displayName);
        model.addAttribute("customerId", "@" + canonicalUsername);
        model.addAttribute("username", canonicalUsername);
        if (targetUsername != null && !targetUsername.trim().isEmpty()) {
            model.addAttribute("targetUsername", targetUsername.trim());
        }
        model.addAttribute("isEmployee", isEmployee);
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("backLabel", backLabel);
        model.addAttribute("transactions", transactions);
        model.addAttribute("monthlyDepositTotal", monthlyDepositTotal);
        model.addAttribute("monthlyWithdrawTotal", monthlyWithdrawTotal);
        if (successMessage != null) model.addAttribute("successMessage", successMessage);
        if (errorMessage != null) model.addAttribute("errorMessage", errorMessage);

        return "card";
    }

    private String resolveFundRedirectUrl(String redirectTo, String targetUsername,
                                          jakarta.servlet.http.HttpServletRequest httpRequest) {
        String referer = httpRequest != null ? httpRequest.getHeader("Referer") : null;
        boolean goCard = (redirectTo != null && redirectTo.startsWith("/card"))
                || (referer != null && referer.contains("/card"));
        if (goCard) {
            if (targetUsername != null && !targetUsername.trim().isEmpty()) {
                return "redirect:/card?username=" + java.net.URLEncoder.encode(targetUsername.trim(), java.nio.charset.StandardCharsets.UTF_8);
            }
            return "redirect:/card";
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/account/deposit")
    public String deposit(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "targetUsername", required = false) String targetUsername,
            @RequestParam(value = "redirectTo", required = false) String redirectTo,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String redirectUrl = resolveFundRedirectUrl(redirectTo, targetUsername, httpRequest);
        try {
            boolean isAdminOrEmp = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                            || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));

            String lookupUsername = (targetUsername != null && !targetUsername.trim().isEmpty() && isAdminOrEmp)
                    ? targetUsername.trim()
                    : authentication.getName();

            com.financeapp.model.User user = userRepository.findFirstByUsernameIgnoreCase(lookupUsername)
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(lookupUsername))
                    .orElse(null);

            String canonicalUsername = user != null ? user.getUsername() : lookupUsername;
            java.util.Optional<com.financeapp.model.BankEmployee> empOpt = bankEmployeeRepository.findByUsername(canonicalUsername);
            com.financeapp.model.Account primaryAccount = resolveOrCreatePrimaryAccount(user, empOpt);

            String accountNumber = primaryAccount != null ? primaryAccount.getAccountNumber() : null;
            if (accountNumber == null) {
                List<AccountResponse> accounts = accountService.getAllAccounts(canonicalUsername);
                if (!accounts.isEmpty()) {
                    accountNumber = accounts.get(0).getAccountNumber();
                }
            }
            if (accountNumber == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Hesab tapılmadı");
                return redirectUrl;
            }

            DepositRequest depositRequest = new DepositRequest();
            depositRequest.setAccountNumber(accountNumber);
            depositRequest.setAmount(amount);

            String memo = (description != null && !description.trim().isEmpty())
                    ? description.trim()
                    : "Balans Artımı / Depozit";
            depositRequest.setDescription(memo);

            AccountResponse updated = accountService.deposit(depositRequest);
            if (empOpt.isPresent()) {
                com.financeapp.model.BankEmployee emp = empOpt.get();
                emp.setBalance(updated.getBalance());
                bankEmployeeRepository.save(emp);
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("+$%,.2f məbləğində vəsait balansınıza əlavə edildi (%s)", amount, memo));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Depozit uğursuz oldu: " + e.getMessage());
        }

        return redirectUrl;
    }

    @PostMapping("/account/withdraw")
    public String withdraw(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "currency", required = false, defaultValue = "AZN") String currency,
            @RequestParam(value = "recipientType", required = false, defaultValue = "INDIVIDUAL") String recipientType,
            @RequestParam(value = "recipientAccountNumber", required = false) String recipientAccountNumber,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "taxId", required = false) String taxId,
            @RequestParam(value = "companyAccount", required = false) String companyAccount,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "targetUsername", required = false) String targetUsername,
            @RequestParam(value = "redirectTo", required = false) String redirectTo,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String redirectUrl = resolveFundRedirectUrl(redirectTo, targetUsername, httpRequest);
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Çıxarış məbləği müsbət olmalıdır");
            }
            boolean isCorporateInput = "CORPORATE".equalsIgnoreCase(recipientType);
            if (isCorporateInput && (companyName == null || companyName.trim().isEmpty())) {
                throw new IllegalArgumentException("Şirkət adı / Müəssisə daxil edilməlidir");
            }
            boolean isAdminOrEmp = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                            || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));

            String lookupUsername = (targetUsername != null && !targetUsername.trim().isEmpty() && isAdminOrEmp)
                    ? targetUsername.trim()
                    : authentication.getName();

            com.financeapp.model.User user = userRepository.findFirstByUsernameIgnoreCase(lookupUsername)
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(lookupUsername))
                    .orElse(null);

            String canonicalUsername = user != null ? user.getUsername() : lookupUsername;
            java.util.Optional<com.financeapp.model.BankEmployee> empOpt = bankEmployeeRepository.findByUsername(canonicalUsername);
            com.financeapp.model.Account primaryAccount = resolveOrCreatePrimaryAccount(user, empOpt);

            String accountNumber = primaryAccount != null ? primaryAccount.getAccountNumber() : null;
            if (accountNumber == null) {
                List<AccountResponse> accounts = accountService.getAllAccounts(canonicalUsername);
                if (!accounts.isEmpty()) {
                    accountNumber = accounts.get(0).getAccountNumber();
                }
            }
            if (accountNumber == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Hesab tapılmadı");
                return redirectUrl;
            }

            WithdrawRequest request = new WithdrawRequest();
            request.setAccountNumber(accountNumber);

            String accountCurrency = com.financeapp.service.CurrencyConverter.normalize(
                    primaryAccount != null ? primaryAccount.getCurrency() : "AZN");
            String selectedCurrency = com.financeapp.service.CurrencyConverter.normalize(currency);
            BigDecimal debitAmount = com.financeapp.service.CurrencyConverter.convert(amount, selectedCurrency, accountCurrency);
            request.setAmount(debitAmount);

            boolean isCorporate = "CORPORATE".equalsIgnoreCase(recipientType);
            String payeeAccount = isCorporate ? companyAccount : recipientAccountNumber;
            String baseMemo = formatRecipientMemo(recipientType, companyName, taxId, description, "Xərcləmə / Çıxarış");
            StringBuilder memoBuilder = new StringBuilder(baseMemo);
            if (payeeAccount != null && !payeeAccount.trim().isEmpty()) {
                memoBuilder.append(isCorporate ? " • Şirkət hesabı: " : " • Alıcı: ").append(payeeAccount.trim());
            }
            if (!selectedCurrency.equals(accountCurrency)) {
                memoBuilder.append(" • ").append(amount.setScale(2, java.math.RoundingMode.HALF_UP)).append(" ")
                        .append(selectedCurrency).append(" → ").append(debitAmount).append(" ").append(accountCurrency);
            }
            String memo = memoBuilder.length() > 250 ? memoBuilder.substring(0, 250) : memoBuilder.toString();
            request.setDescription(memo);

            AccountResponse updated = accountService.withdraw(request);
            if (empOpt.isPresent()) {
                com.financeapp.model.BankEmployee emp = empOpt.get();
                emp.setBalance(updated.getBalance());
                bankEmployeeRepository.save(emp);
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("%,.2f %s məbləğində vəsait çıxarıldı (%s)", amount, selectedCurrency, memo));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Çıxarış uğursuz oldu: " + e.getMessage());
        }

        return redirectUrl;
    }

    @PostMapping("/account/transfer")
    public String transfer(
            @RequestParam(value = "recipientAccountNumber", required = false) String recipientAccountNumber,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "currency", required = false, defaultValue = "AZN") String currency,
            @RequestParam(value = "recipientType", required = false, defaultValue = "INDIVIDUAL") String recipientType,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "taxId", required = false) String taxId,
            @RequestParam(value = "companyAccount", required = false) String companyAccount,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "targetUsername", required = false) String targetUsername,
            @RequestParam(value = "redirectTo", required = false) String redirectTo,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String redirectUrl = resolveFundRedirectUrl(redirectTo, targetUsername, httpRequest);
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Köçürmə məbləği müsbət olmalıdır");
            }
            boolean isAdminOrEmp = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE")
                            || a.getAuthority().equalsIgnoreCase("ROLE_SECURITY")
                            || a.getAuthority().equalsIgnoreCase("ROLE_EMPLOYEE_PENDING"));
            String lookupActor = (targetUsername != null && !targetUsername.trim().isEmpty() && isAdminOrEmp)
                    ? targetUsername.trim()
                    : authentication.getName();
            com.financeapp.model.User actorUser = userRepository.findFirstByUsernameIgnoreCase(lookupActor)
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(lookupActor))
                    .orElse(null);
            String canonicalActor = actorUser != null ? actorUser.getUsername() : lookupActor;

            boolean isCorporate = "CORPORATE".equalsIgnoreCase(recipientType);
            String effectiveRecipient;
            if (isCorporate) {
                if (companyName == null || companyName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Şirkət adı / Müəssisə daxil edilməlidir");
                }
                effectiveRecipient = (companyAccount != null && !companyAccount.trim().isEmpty())
                        ? companyAccount.trim()
                        : companyName.trim();
            } else {
                effectiveRecipient = recipientAccountNumber;
                if (effectiveRecipient == null || effectiveRecipient.trim().isEmpty()) {
                    throw new IllegalArgumentException("Alıcı hesab nömrəsi və ya @istifadəçi adı daxil edilməlidir");
                }
            }
            String cleanRecipient = effectiveRecipient.trim();
            if (!isCorporate && cleanRecipient.startsWith("@")) {
                cleanRecipient = cleanRecipient.substring(1).trim();
            }

            com.financeapp.dto.request.TransferRequest request = new com.financeapp.dto.request.TransferRequest();
            request.setRecipientAccountNumber(cleanRecipient);
            request.setAmount(amount);
            request.setCurrency(currency);
            request.setDescription(formatRecipientMemo(recipientType, companyName, taxId, description, "Pul Köçürməsi"));

            com.financeapp.dto.response.TransferResponse response = isCorporate
                    ? accountService.corporateTransfer(request, canonicalActor, companyName)
                    : accountService.transfer(request, canonicalActor);
            String sentCur = response.getSentCurrency() != null ? response.getSentCurrency() : "AZN";
            String recCur = response.getRecipientCurrency() != null ? response.getRecipientCurrency() : sentCur;
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Köçürmə uğurla tamamlandı! %s hesabına %s %s göndərildi (Qəbulediciyə çevrilən: %s %s, Komissiya: %s, Balansdan çıxılan: %s). Ref: %s",
                            response.getRecipientName(),
                            response.getGrossAmount(),
                            sentCur,
                            response.getConvertedAmount() != null ? response.getConvertedAmount() : response.getNetAmount(),
                            recCur,
                            response.getCommissionFee(),
                            response.getTotalDeducted(),
                            response.getReferenceNumber()));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Köçürmə uğursuz oldu: " + e.getMessage());
        }

        return redirectUrl;
    }

    private String formatRecipientMemo(String recipientType, String companyName, String taxId, String description, String defaultLabel) {
        StringBuilder sb = new StringBuilder();
        if ("CORPORATE".equalsIgnoreCase(recipientType)) {
            sb.append("[Şirkət");
            if (companyName != null && !companyName.trim().isEmpty()) {
                sb.append(": ").append(companyName.trim());
            }
            if (taxId != null && !taxId.trim().isEmpty()) {
                sb.append(" | VÖEN: ").append(taxId.trim());
            }
            sb.append("] ");
        } else {
            sb.append("[Şəxsi] ");
        }
        if (description != null && !description.trim().isEmpty()) {
            sb.append(description.trim());
        } else {
            sb.append(defaultLabel);
        }
        return sb.toString().trim();
    }

    private List<com.financeapp.model.Transaction> loadMergedTransactions(
            com.financeapp.model.User user,
            com.financeapp.model.Account account) {
        java.util.LinkedHashMap<Long, com.financeapp.model.Transaction> merged = new java.util.LinkedHashMap<>();
        if (user != null) {
            for (com.financeapp.model.Transaction t : transactionRepository.findByUserOrderByTimestampDesc(user)) {
                if (t.getId() != null) merged.putIfAbsent(t.getId(), t);
            }
        }
        if (account != null) {
            for (com.financeapp.model.Transaction t : transactionRepository.findByAccountOrderByTimestampDesc(account)) {
                if (t.getId() != null) merged.putIfAbsent(t.getId(), t);
            }
        }
        List<com.financeapp.model.Transaction> result = new java.util.ArrayList<>(merged.values());
        result.sort((a, b) -> {
            java.time.LocalDateTime ta = a.getTimestamp();
            java.time.LocalDateTime tb = b.getTimestamp();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return result;
    }
}
