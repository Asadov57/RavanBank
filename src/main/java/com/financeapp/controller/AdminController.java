package com.financeapp.controller;

import com.financeapp.dto.request.AccountCreateRequest;
import com.financeapp.dto.request.WithdrawRequest;
import com.financeapp.dto.response.AccountResponse;
import com.financeapp.enums.AccountStatus;
import com.financeapp.model.BankEmployee;
import com.financeapp.model.BankVault;
import com.financeapp.model.User;
import com.financeapp.model.VaultTransaction;
import com.financeapp.repository.BankEmployeeRepository;
import com.financeapp.repository.BankVaultRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.repository.VaultTransactionRepository;
import com.financeapp.service.AccountService;
import com.financeapp.service.AuditLogService;
import com.financeapp.service.BankVaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AccountService accountService;
    private final BankVaultService bankVaultService;
    private final BankVaultRepository bankVaultRepository;
    private final VaultTransactionRepository vaultTransactionRepository;
    private final UserRepository userRepository;
    private final BankEmployeeRepository bankEmployeeRepository;
    private final com.financeapp.repository.AccountRepository accountRepository;
    private final com.financeapp.repository.TransactionRepository transactionRepository;
    private final com.financeapp.repository.PanelMessageRepository panelMessageRepository;
    private final AuditLogService auditLogService;
    private final com.financeapp.service.DepartmentQuotaService departmentQuotaService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    public AdminController(AccountService accountService,
            BankVaultService bankVaultService,
            BankVaultRepository bankVaultRepository,
            VaultTransactionRepository vaultTransactionRepository,
            UserRepository userRepository,
            BankEmployeeRepository bankEmployeeRepository,
            com.financeapp.repository.AccountRepository accountRepository,
            com.financeapp.repository.TransactionRepository transactionRepository,
            com.financeapp.repository.PanelMessageRepository panelMessageRepository,
            AuditLogService auditLogService,
            com.financeapp.service.DepartmentQuotaService departmentQuotaService) {
        this.accountService = accountService;
        this.bankVaultService = bankVaultService;
        this.bankVaultRepository = bankVaultRepository;
        this.vaultTransactionRepository = vaultTransactionRepository;
        this.userRepository = userRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.panelMessageRepository = panelMessageRepository;
        this.auditLogService = auditLogService;
        this.departmentQuotaService = departmentQuotaService;
    }

    @GetMapping
    public String showAdminPage(@RequestParam(value = "status", required = false) AccountStatus status,
            @RequestParam(value = "successMessage", required = false) String successMessageParam,
            @RequestParam(value = "errorMessage", required = false) String errorMessageParam,
            Principal principal,
            Model model) {

        org.springframework.security.core.Authentication auth
                = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
        if (!isAdmin) {
            return "redirect:/dashboard";
        }

        try {
            List<AccountResponse> accounts;
            if (status != null) {
                accounts = accountService.getAccountsByStatus(status);
            } else {
                accounts = accountService.getAllAccountsForAdmin();
            }

            List<AccountResponse> allAccounts = accountService.getAllAccountsForAdmin();
            List<AccountResponse> blockedAccounts = accountService.getAccountsByStatus(AccountStatus.BLOCKED);

            BigDecimal vaultBalance = bankVaultService.getVaultBalance();
            List<VaultTransaction> vaultTransactions = bankVaultService.getAllTransactions();

            long activeCount = accountService.countByStatus(AccountStatus.ACTIVE);
            long blockedCount = blockedAccounts.size();
            long pendingCount = accountService.countByStatus(AccountStatus.PENDING);

            List<BankEmployee> employees = bankEmployeeRepository.findAllByOrderByCreatedAtDesc();
            for (BankEmployee emp : employees) {
                ensureEmployeeBankAccount(emp);
            }
            List<BankEmployee> escalatedEmployees = employees.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getEscalatedToAdmin())
                            || "SENT_TO_ADMIN".equalsIgnoreCase(e.getApprovalStatus()))
                    .toList();
            long pendingEmployees = bankEmployeeRepository.countByApprovalStatus("PENDING");
            long activeEmployees = bankEmployeeRepository.countByAccountStatus("ACTIVE");

            BigDecimal inflowTotal = vaultTransactions != null ? vaultTransactions.stream()
                    .filter(t -> "INFLOW".equalsIgnoreCase(t.getTransactionType()))
                    .map(VaultTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;

            BigDecimal outflowTotal = vaultTransactions != null ? vaultTransactions.stream()
                    .filter(t -> "OUTFLOW".equalsIgnoreCase(t.getTransactionType()))
                    .map(VaultTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;

            model.addAttribute("accounts", accounts);
            model.addAttribute("allAccounts", allAccounts);
            model.addAttribute("blockedAccounts", blockedAccounts);
            model.addAttribute("vaultBalance", vaultBalance);
            model.addAttribute("vaultBalanceFormatted", String.format("€%,.2f", vaultBalance));
            model.addAttribute("vaultBalanceAzn", String.format("₼%,.2f", com.financeapp.service.CurrencyConverter.convert(vaultBalance, "EUR", "AZN")));
            model.addAttribute("vaultBalanceUsd", String.format("$%,.2f", com.financeapp.service.CurrencyConverter.convert(vaultBalance, "EUR", "USD")));
            model.addAttribute("vaultTransactions", vaultTransactions != null ? vaultTransactions : Collections.emptyList());
            model.addAttribute("inflowTotal", inflowTotal);
            model.addAttribute("inflowTotalFormatted", String.format("€%,.2f", inflowTotal));
            model.addAttribute("outflowTotal", outflowTotal);
            model.addAttribute("outflowTotalFormatted", String.format("€%,.2f", outflowTotal));
            model.addAttribute("activeAccountsCount", activeCount);
            model.addAttribute("blockedAccountsCount", blockedCount);
            model.addAttribute("pendingAccountsCount", pendingCount);
            model.addAttribute("selectedStatus", status != null ? status.name().toLowerCase() : "all");

            model.addAttribute("employees", employees);
            model.addAttribute("escalatedEmployees", escalatedEmployees);
            model.addAttribute("escalatedEmployeesCount", escalatedEmployees.size());
            model.addAttribute("totalEmployeesCount", employees.size());
            model.addAttribute("pendingEmployeesCount", pendingEmployees);
            model.addAttribute("activeEmployeesCount", activeEmployees);
            departmentQuotaService.populateQuotaAttributes(model);
            model.addAttribute("panelMessages", panelMessageRepository.findByFromPanelOrToPanelOrderByCreatedAtDesc("ADMIN", "ADMIN"));
            model.addAttribute("adminUsers", userRepository.findAll());

        } catch (Exception e) {
            model.addAttribute("accounts", Collections.emptyList());
            model.addAttribute("allAccounts", Collections.emptyList());
            model.addAttribute("blockedAccounts", Collections.emptyList());
            model.addAttribute("vaultTransactions", Collections.emptyList());
            model.addAttribute("employees", Collections.emptyList());
            model.addAttribute("panelMessages", Collections.emptyList());
            model.addAttribute("adminUsers", Collections.emptyList());
            model.addAttribute("activeAccountsCount", 0L);
            model.addAttribute("blockedAccountsCount", 0L);
            model.addAttribute("pendingAccountsCount", 0L);
            model.addAttribute("selectedStatus", "all");
            model.addAttribute("vaultBalance", BigDecimal.ZERO);
            model.addAttribute("vaultBalanceFormatted", "€0.00");
            model.addAttribute("errorMessage", "Məlumatlar yüklənərkən xəta baş verdi: " + e.getMessage());
        }

        String adminUsername = principal != null ? principal.getName() : "Ravan Asadov";
        model.addAttribute("adminUsername", adminUsername);
        String initials = "RA";
        if (adminUsername != null && !adminUsername.trim().isEmpty()) {
            String[] parts = adminUsername.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
            } else if (adminUsername.length() >= 2) {
                initials = adminUsername.substring(0, 2).toUpperCase();
            }
        }
        model.addAttribute("adminInitials", initials);

        if (successMessageParam != null && !model.containsAttribute("successMessage")) {
            model.addAttribute("successMessage", successMessageParam);
        }
        if (errorMessageParam != null && !model.containsAttribute("errorMessage")) {
            model.addAttribute("errorMessage", errorMessageParam);
        }

        if (!model.containsAttribute("createRequest")) {
            model.addAttribute("createRequest", new AccountCreateRequest());
        }
        if (!model.containsAttribute("withdrawRequest")) {
            model.addAttribute("withdrawRequest", new WithdrawRequest());
        }

        return "admin";
    }

    @GetMapping("/accounts/blocked")
    public String showBlockedAccounts(Principal principal, Model model) {
        return showAdminPage(AccountStatus.BLOCKED, null, null, principal, model);
    }

    @GetMapping("/api/accounts")
    @ResponseBody
    public List<AccountResponse> getAccountsApi(@RequestParam(value = "status", required = false) AccountStatus status) {
        if (status != null) {
            return accountService.getAccountsByStatus(status);
        }
        return accountService.getAllAccountsForAdmin();
    }

    @PostMapping("/accounts/create")
    public String createAccount(@ModelAttribute("createRequest") AccountCreateRequest request,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                "Admin panelindən birbaşa hesab yaratmaq ləğv edilmişdir. Müştəri yalnız öz nömrəsi və 3 rəqəmli SMS təsdiq kodu ilə qeydiyyatdan keçə bilər.");
        return "redirect:/admin";
    }

    @PostMapping("/vault/credit")
    public String issueCredit(@RequestParam("accountNumber") String accountNumber,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "interestRate", required = false, defaultValue = "12.0") BigDecimal interestRate,
            @RequestParam(value = "note", required = false) String note,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            BigDecimal rate = (interestRate != null && interestRate.compareTo(BigDecimal.ZERO) >= 0)
                    ? interestRate : new BigDecimal("12.0");
            BigDecimal interestAmount = amount.multiply(rate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal totalRepayment = amount.add(interestAmount);

            bankVaultService.issueCustomerCredit(accountNumber, amount, rate, note);
            evictAllAccountCaches();

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "ISSUE_CREDIT", "ACCOUNT", accountNumber,
                    "Kredit ayrıldı: " + amount + " ₼, Hesab: " + accountNumber);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Müştəriyə kredit təsdiqləndi: $%,.2f | Faiz: %s%% ($%,.2f) | Ümumi Geri Ödəniş: $%,.2f (Hesab: %s)",
                            amount, rate, interestAmount, totalRepayment, accountNumber));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kredit verilməsi xətası: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/vault/transfer")
    public String transferToCustomer(@RequestParam("accountNumber") String accountNumber,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "note", required = false) String note,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            bankVaultService.transferToCustomer(accountNumber, amount, note);
            evictAllAccountCaches();
            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "VAULT_TRANSFER", "ACCOUNT", accountNumber,
                    "Bankdan müştəriyə köçürmə: " + amount + " ₼");

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Müştəriyə köçürmə tamamlandı: $%,.2f (Hesab: %s)", amount, accountNumber));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Köçürmə xətası: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/accounts/{id}/block")
    public Object blockAccount(@PathVariable Long id,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            accountService.blockAccount(id);
            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "BLOCK_ACCOUNT", "ACCOUNT", id.toString(), "Hesab bloklandı");

            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.ok(Map.of("success", true, "id", id, "status", "BLOCKED", "message", "Hesab #" + id + " bloklandı."));
            }
            redirectAttributes.addFlashAttribute("successMessage", "Hesab #" + id + " bloklandı.");
        } catch (Exception e) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Hesabı bloklamaq mümkün olmadı: " + e.getMessage());
        }
        return "redirect:/admin#customerAccountsSection";
    }

    @PostMapping("/accounts/{id}/unblock")
    public Object unblockAccount(@PathVariable Long id,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            accountService.unblockAccount(id);
            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "UNBLOCK_ACCOUNT", "ACCOUNT", id.toString(), "Hesab blokdan çıxarıldı");

            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.ok(Map.of("success", true, "id", id, "status", "ACTIVE", "message", "Hesab #" + id + " blokdan çıxarıldı."));
            }
            redirectAttributes.addFlashAttribute("successMessage", "Hesab #" + id + " blokdan çıxarıldı.");
        } catch (Exception e) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Blokdan çıxarmaq mümkün olmadı: " + e.getMessage());
        }
        return "redirect:/admin#customerAccountsSection";
    }

    @PostMapping("/employees/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String approveEmployee(
            @PathVariable Long id,
            @RequestParam(value = "department", required = false) String departmentParam,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + id));

            if ("PENDING".equalsIgnoreCase(employee.getApprovalStatus()) && !Boolean.TRUE.equals(employee.getEscalatedToAdmin())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Yeni işçi təsdiqinə əvvəlcə Sorğu və Security panelləri baxır! Problem yarandıqda Adminə yönləndirilə bilər.");
                return "redirect:/admin#employeesSection";
            }

            String rawDept = departmentParam != null && !departmentParam.trim().isEmpty()
                    ? departmentParam.trim() : (employee.getDepartment() != null ? employee.getDepartment().trim() : "Inquiry");

            String targetDept = departmentQuotaService.normalizeDepartmentName(rawDept);
            String quotaError = departmentQuotaService.checkQuotaError(targetDept, null);
            if (quotaError != null) {
                redirectAttributes.addFlashAttribute("errorMessage", quotaError);
                return "redirect:/admin#employeesSection";
            }

            String adminName = principal != null ? principal.getName() : "Admin";

            employee.setDepartment(targetDept);
            employee.setApprovalStatus("APPROVED");
            employee.setAccountStatus("ACTIVE");
            employee.setApprovedBy(adminName);
            employee.setApprovedAt(LocalDateTime.now());
            employee.setEscalatedToAdmin(false);
            if ("Security".equalsIgnoreCase(targetDept)) {
                employee.setRole("ROLE_SECURITY");
            } else {
                employee.setRole("ROLE_EMPLOYEE");
            }
            bankEmployeeRepository.save(employee);

            final String assignedRole = employee.getRole();
            userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(u -> {
                u.setRole(assignedRole);
                userRepository.save(u);
                accountRepository.findFirstByUser(u).ifPresent(acc -> {
                    acc.setStatus(AccountStatus.ACTIVE);
                    accountRepository.save(acc);
                    employee.setAccountNumber(acc.getAccountNumber());
                    employee.setBalance(acc.getBalance());
                });
            });
            ensureEmployeeBankAccount(employee);
            bankEmployeeRepository.save(employee);

            auditLogService.logAction(adminName, "APPROVE_EMPLOYEE", "EMPLOYEE",
                    employee.getEmployeeId(), "Əməkdaş təsdiqləndi: " + employee.getFullName() + " (" + targetDept + ")");

            redirectAttributes.addFlashAttribute("successMessage",
                    "Əməkdaş (" + employee.getFullName() + " - " + employee.getEmployeeId() + ") uğurla TƏSDİQLƏNDİ və " + targetDept + " şöbəsinə təyin edildi!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Əməkdaş təsdiqlənərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/block")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String blockEmployee(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));

            employee.setAccountStatus("BLOCKED");
            employee.setEscalatedToAdmin(false);
            bankEmployeeRepository.save(employee);

            userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(u -> {
                u.setRole("ROLE_BLOCKED");
                userRepository.save(u);
            });

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "BLOCK_EMPLOYEE", "EMPLOYEE",
                    employee.getEmployeeId(), "Əməkdaş BLOKLANDI: " + employee.getFullName());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Əməkdaş " + employee.getFullName() + " (" + employee.getEmployeeId() + ") hesabı BLOKLANDI.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Əməkdaş bloklanarkən xəta: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/unblock")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String unblockEmployee(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));

            employee.setAccountStatus("ACTIVE");
            employee.setEscalatedToAdmin(false);
            bankEmployeeRepository.save(employee);

            String roleToRestore = "Security".equalsIgnoreCase(employee.getDepartment()) ? "ROLE_SECURITY" : "ROLE_EMPLOYEE";
            userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(u -> {
                u.setRole(roleToRestore);
                userRepository.save(u);
            });

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "UNBLOCK_EMPLOYEE", "EMPLOYEE",
                    employee.getEmployeeId(), "Əməkdaş BLOKDAN ÇIXARILDI: " + employee.getFullName());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Əməkdaş " + employee.getFullName() + " (" + employee.getEmployeeId() + ") hesabı BLOKDAN ÇIXARILDI və aktivləşdirildi.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Əməkdaş blokdan çıxarılarkən xəta: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/update-salary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String updateSalary(
            @PathVariable Long id,
            @RequestParam("salary") BigDecimal salary,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));

            employee.setSalary(salary);
            bankEmployeeRepository.save(employee);

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "UPDATE_SALARY", "EMPLOYEE",
                    employee.getEmployeeId(), employee.getFullName() + " üçün maaş təyin edildi: ₼" + salary);

            redirectAttributes.addFlashAttribute("successMessage",
                    employee.getFullName() + " üçün maaş məbləği uğurla təyin edildi: ₼" + String.format("%,.2f", salary));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Maaş təyin edilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/pay-salary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String paySalary(
            @PathVariable Long id,
            @RequestParam(value = "amount", required = false) BigDecimal customAmount,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));

            if ("BLOCKED".equalsIgnoreCase(employee.getAccountStatus())
                    || "REJECTED".equalsIgnoreCase(employee.getApprovalStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Bloklanmış və ya rədd edilmiş əməkdaşa maaş ödənilə bilməz (Hazırkı status: " + employee.getAccountStatus() + ").");
                return "redirect:/admin#employeesSection";
            }

            if (!"APPROVED".equalsIgnoreCase(employee.getApprovalStatus())) {
                employee.setApprovalStatus("APPROVED");
            }
            if (!"ACTIVE".equalsIgnoreCase(employee.getAccountStatus())) {
                employee.setAccountStatus("ACTIVE");
            }

            BigDecimal salaryAmount;
            if (customAmount != null && customAmount.compareTo(BigDecimal.ZERO) > 0) {
                salaryAmount = customAmount;
                employee.setSalary(customAmount);
            } else if (employee.getSalary() != null && employee.getSalary().compareTo(BigDecimal.ZERO) > 0) {
                salaryAmount = employee.getSalary();
            } else {
                salaryAmount = new BigDecimal("1500.00");
                employee.setSalary(salaryAmount);
            }

            BankVault vault = bankVaultRepository.findFirstByOrderByIdAsc()
                    .orElseThrow(() -> new RuntimeException("Bank Xəzinəsi tapılmadı."));

            if (vault.getBalance().compareTo(salaryAmount) < 0) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Bank Xəzinəsində ₼" + salaryAmount + " məbləğində maaş ödəmək üçün kifayət qədər vəsait yoxdur (Xəzinə qalığı: ₼" + vault.getBalance() + ").");
                return "redirect:/admin#employeesSection";
            }

            vault.setBalance(vault.getBalance().subtract(salaryAmount));
            bankVaultRepository.saveAndFlush(vault);

            ensureEmployeeBankAccount(employee);
            com.financeapp.model.User empUser = userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).orElse(null);
            com.financeapp.model.Account empAcc = null;
            if (employee.getAccountNumber() != null && !employee.getAccountNumber().isEmpty()) {
                empAcc = accountRepository.findByAccountNumber(employee.getAccountNumber()).orElse(null);
            }
            if (empAcc == null && empUser != null) {
                empAcc = accountRepository.findFirstByUser(empUser).orElse(null);
            }

            BigDecimal newBal;
            if (empAcc != null) {
                if (empUser == null) {
                    empUser = empAcc.getUser();
                }
                BigDecimal currentBal = empAcc.getBalance() != null ? empAcc.getBalance() : BigDecimal.ZERO;
                newBal = currentBal.add(salaryAmount);
                empAcc.setBalance(newBal);
                empAcc.setStatus(AccountStatus.ACTIVE);
                empAcc = accountRepository.saveAndFlush(empAcc);
                employee.setAccountNumber(empAcc.getAccountNumber());
                employee.setBalance(newBal);
            } else {
                BigDecimal currentBal = employee.getBalance() != null ? employee.getBalance() : BigDecimal.ZERO;
                newBal = currentBal.add(salaryAmount);
                employee.setBalance(newBal);
            }

            VaultTransaction tx = new VaultTransaction(
                    salaryAmount,
                    "OUTFLOW",
                    "SALARY",
                    employee.getFullName() + " (@" + employee.getUsername() + ") - Hesab: #" + (employee.getAccountNumber() != null ? employee.getAccountNumber() : "N/A"),
                    adminName(principal) + " tərəfindən " + employee.getDepartment() + " əməkdaşına ödənildi",
                    vault.getBalance()
            );
            vaultTransactionRepository.saveAndFlush(tx);

            String ref = "TX-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-SAL";
            String txDesc = "Aylıq Əməkhaqqı Köçürməsi • Mərkəzi Xəzinə"
                    + (empAcc != null ? " (#" + empAcc.getAccountNumber() + ")" : "");
            com.financeapp.model.Transaction empTx = new com.financeapp.model.Transaction(
                    salaryAmount,
                    txDesc,
                    "DEPOSIT",
                    ref,
                    empAcc,
                    empUser
            );
            transactionRepository.saveAndFlush(empTx);

            employee.setLastSalaryPaidAt(LocalDateTime.now());
            bankEmployeeRepository.saveAndFlush(employee);
            evictAllAccountCaches();

            String adminName = adminName(principal);
            auditLogService.logAction(adminName, "PAY_SALARY", "EMPLOYEE",
                    employee.getEmployeeId(), employee.getFullName() + " üçün €" + salaryAmount + " maaş ödənildi (Hesab: " + employee.getAccountNumber() + ")");

            redirectAttributes.addFlashAttribute("successMessage",
                    employee.getFullName() + " üçün €" + String.format("%,.2f", salaryAmount) + " məbləğində maaş Mərkəzi Xəzinədən işçinin şəxsi hesabına (" + employee.getAccountNumber() + ") uğurla köçürüldü! Yeni balans: €" + String.format("%,.2f", employee.getBalance()));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Maaş ödənilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    private void evictAllAccountCaches() {
        if (cacheManager == null) {
            return;
        }
        String[] cacheNames = {"accounts", "accountById", "accountByNumber", "adminAccounts", "accountsByStatus"};
        for (String name : cacheNames) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private String adminName(Principal principal) {
        return principal != null ? principal.getName() : "Admin";
    }

    private void ensureEmployeeBankAccount(BankEmployee emp) {
        if (emp == null) {
            return;
        }
        boolean empDirty = false;
        if (emp.getSalary() == null || emp.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            emp.setSalary(new BigDecimal("1500.00"));
            empDirty = true;
        }
        User empUser = userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).orElse(null);
        if (empUser == null && emp.getUsername() != null && !emp.getUsername().trim().isEmpty()) {
            User created = new User();
            created.setUsername(emp.getUsername().trim());
            created.setEmail(emp.getEmail());
            created.setFullName(emp.getFullName() != null ? emp.getFullName() : emp.getUsername());
            created.setPhoneNumber(emp.getPhoneNumber());
            created.setPassword("$2a$10$7EqJtq98hPqEX7fNZaFWoOeR0rP1wZ9j7Z0n7Y9kQ2wY8aN1mP6qS");
            created.setRole(emp.getRole() != null && !emp.getRole().trim().isEmpty() ? emp.getRole() : "ROLE_EMPLOYEE");
            empUser = userRepository.saveAndFlush(created);
        }
        if (empUser != null) {
            final User linkedUser = empUser;
            if (emp.getUserId() == null || !emp.getUserId().equals(linkedUser.getId())) {
                emp.setUserId(linkedUser.getId());
                empDirty = true;
            }
            com.financeapp.model.Account empAcc = null;
            if (emp.getAccountNumber() != null && !emp.getAccountNumber().trim().isEmpty()) {
                empAcc = accountRepository.findByAccountNumber(emp.getAccountNumber().trim()).orElse(null);
            }
            if (empAcc == null) {
                empAcc = accountRepository.findFirstByUser(linkedUser).orElseGet(() -> {
                    com.financeapp.model.Account acc = new com.financeapp.model.Account();
                    acc.setAccountNumber(emp.getAccountNumber() != null && !emp.getAccountNumber().isEmpty()
                            ? emp.getAccountNumber()
                            : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                    acc.setBalance(emp.getBalance() != null ? emp.getBalance() : BigDecimal.ZERO);
                    acc.setCurrency("EUR");
                    acc.setAccountType("CHECKING");
                    acc.setStatus("BLOCKED".equalsIgnoreCase(emp.getAccountStatus()) ? AccountStatus.BLOCKED : AccountStatus.ACTIVE);
                    acc.setUser(linkedUser);
                    return accountRepository.saveAndFlush(acc);
                });
            }
            if (emp.getAccountNumber() == null || !emp.getAccountNumber().equals(empAcc.getAccountNumber())) {
                emp.setAccountNumber(empAcc.getAccountNumber());
                empDirty = true;
            }
            BigDecimal accBal = empAcc.getBalance() != null ? empAcc.getBalance() : BigDecimal.ZERO;
            if (emp.getBalance() == null || emp.getBalance().compareTo(accBal) != 0) {
                emp.setBalance(accBal);
                empDirty = true;
            }
        } else if (emp.getAccountNumber() == null || emp.getAccountNumber().isEmpty()) {
            emp.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
            if (emp.getBalance() == null) {
                emp.setBalance(BigDecimal.ZERO);
            }
            empDirty = true;
        }
        if (empDirty) {
            bankEmployeeRepository.saveAndFlush(emp);
        }
    }

    @PostMapping("/employees/{id}/assign-department")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String assignDepartment(
            @PathVariable Long id,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "position", required = false) String position,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + id));

            String rawInput = (position != null && !position.trim().isEmpty()) ? position.trim() : department;
            String targetDept = departmentQuotaService.normalizeDepartmentName(rawInput);
            String quotaError = departmentQuotaService.checkQuotaError(targetDept, employee.getDepartment());
            if (quotaError != null) {
                redirectAttributes.addFlashAttribute("errorMessage", quotaError);
                return "redirect:/admin#employeesSection";
            }

            employee.setDepartment(targetDept);
            if (position != null && !position.trim().isEmpty()) {
                employee.setPosition(position.trim());
            } else {
                employee.setPosition(switch (targetDept) {
                    case "Security" ->
                        "Security";
                    case "Treasury" ->
                        "Xəzinədar";
                    case "IT" ->
                        "İT Mütəxəssisi";
                    case "Customer Service" ->
                        "Müştəri Xidmətləri";
                    case "HR" ->
                        "İnsan Resursları";
                    default ->
                        "Operator";
                });
            }
            if ("Security".equalsIgnoreCase(targetDept)) {
                employee.setRole("ROLE_SECURITY");
            } else {
                employee.setRole("ROLE_EMPLOYEE");
            }
            bankEmployeeRepository.save(employee);

            final String roleToSave = employee.getRole();
            userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(u -> {
                u.setRole(roleToSave);
                userRepository.save(u);
            });

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "ASSIGN_DEPARTMENT", "EMPLOYEE",
                    employee.getEmployeeId(), "Assigned to department: " + targetDept);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Employee " + employee.getFullName() + " assigned to " + targetDept + " Department.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error assigning department: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String toggleEmployeeStatus(
            @PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + id));

            String newStatus = "ACTIVE".equalsIgnoreCase(employee.getAccountStatus()) ? "DISABLED" : "ACTIVE";
            employee.setAccountStatus(newStatus);
            bankEmployeeRepository.save(employee);

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "TOGGLE_EMPLOYEE_STATUS", "EMPLOYEE",
                    employee.getEmployeeId(), "Status changed to: " + newStatus);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Employee (" + employee.getFullName() + ") status set to " + newStatus + ".");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating employee status: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/update-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String updateEmployeeRole(
            @PathVariable Long id,
            @RequestParam("role") String role,
            @RequestParam(value = "position", required = false) String position,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + id));

            employee.setRole(role);
            if (position != null && !position.trim().isEmpty()) {
                employee.setPosition(position.trim());
            }
            bankEmployeeRepository.save(employee);

            userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(u -> {
                u.setRole(role);
                userRepository.save(u);
            });

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "UPDATE_EMPLOYEE_ROLE", "EMPLOYEE",
                    employee.getEmployeeId(), "Role updated to: " + role);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Employee (" + employee.getFullName() + ") role updated to " + role + ".");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating employee role: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/employees/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String rejectEmployee(
            @PathVariable Long id,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + id));

            employee.setApprovalStatus("REJECTED");
            employee.setAccountStatus("DISABLED");
            employee.setEscalatedToAdmin(false);
            employee.setNotes(reason != null ? reason.trim() : "Application rejected by Admin.");
            bankEmployeeRepository.save(employee);

            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "REJECT_EMPLOYEE", "EMPLOYEE",
                    employee.getEmployeeId(), "Employee application rejected.");

            redirectAttributes.addFlashAttribute("successMessage",
                    "Employee application rejected.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error rejecting employee: " + e.getMessage());
        }

        return "redirect:/admin#employeesSection";
    }

    @PostMapping("/corporate/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String corporateTransfer(
            @RequestParam("companyName") String companyName,
            @RequestParam(value = "targetAccountNumber", required = false) String accountNumber,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "currency", defaultValue = "EUR") String currency,
            @RequestParam(value = "note", required = false) String note,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "Transfer məbləği 0-dan böyük olmalıdır.");
                return "redirect:/admin#corporateTransferSection";
            }
            String cleanCompany = (companyName != null && !companyName.trim().isEmpty())
                    ? companyName.trim() : "Korporativ Şirkət";
            String actor = principal != null ? principal.getName() : "Admin";
            String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
            BigDecimal eurAmount = com.financeapp.service.CurrencyConverter.convert(amount, normCur, "EUR");

            bankVaultService.recordWithdrawal(eurAmount,
                    cleanCompany,
                    accountNumber != null && !accountNumber.trim().isEmpty() ? accountNumber.trim() : "CORPORATE",
                    "Şirkət Transferi (" + amount + " " + normCur + " / " + actor + ")" + (note != null && !note.trim().isEmpty() ? ": " + note.trim() : ""));

            if (accountNumber != null && !accountNumber.trim().isEmpty()) {
                accountRepository.findByAccountNumber(accountNumber.trim()).ifPresent(acc -> {
                    String accCur = com.financeapp.service.CurrencyConverter.normalize(acc.getCurrency());
                    BigDecimal creditAmt = com.financeapp.service.CurrencyConverter.convert(amount, normCur, accCur);
                    BigDecimal cur = acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO;
                    acc.setBalance(cur.add(creditAmt));
                    accountRepository.save(acc);

                    com.financeapp.model.Transaction tx = new com.financeapp.model.Transaction();
                    tx.setAccount(acc);
                    tx.setAmount(creditAmt);
                    tx.setType("DEPOSIT");
                    tx.setDescription("Admin Şirkət Yükləməsi: " + cleanCompany + " (" + amount + " " + normCur + " → " + creditAmt + " " + accCur + ")");
                    tx.setTimestamp(LocalDateTime.now());
                    transactionRepository.save(tx);
                });
            }

            auditLogService.logAction(actor, "CORPORATE_TRANSFER", "COMPANY", cleanCompany,
                    String.format("Şirkətə %,.2f %s (€%,.2f) transfer edildi.", amount, normCur, eurAmount));
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("%s şirkətinə %,.2f %s (€%,.2f ekvivalenti) məbləğində vəsait uğurla transfer edildi.", cleanCompany, amount, normCur, eurAmount));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Şirkət transferi xətası: " + e.getMessage());
        }
        return "redirect:/admin#corporateTransferSection";
    }

    @PostMapping("/employees/{id}/resolve-escalation")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public String resolveEmployeeEscalation(@PathVariable Long id,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            BankEmployee employee = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            employee.setEscalatedToAdmin(false);
            bankEmployeeRepository.save(employee);
            String adminName = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(adminName, "RESOLVE_EMPLOYEE_ESCALATION", "EMPLOYEE",
                    employee.getEmployeeId(), "Yönləndirilmiş işçi məsələsi Admin tərəfindən həll edildi.");
            redirectAttributes.addFlashAttribute("successMessage",
                    employee.getFullName() + " üzrə yönləndirilmiş məsələ həll edildi və siyahıdan bağlandı.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəta: " + e.getMessage());
        }
        return "redirect:/admin#employeesSection";
    }
}
