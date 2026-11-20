package com.financeapp.controller;

import com.financeapp.dto.response.AccountResponse;
import com.financeapp.enums.AccountStatus;
import com.financeapp.model.*;
import com.financeapp.repository.*;
import com.financeapp.service.AccountService;
import com.financeapp.service.AuditLogService;
import com.financeapp.service.BankVaultService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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

@Controller
@RequestMapping("/admin/treasury")
public class TreasuryController {

    private static final String TREASURY_CODE = "RavanXaznadar";
    private static final String SESSION_KEY = "TREASURY_AUTHENTICATED";

    private final BankVaultService bankVaultService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerInquiryRepository customerInquiryRepository;
    private final BankEmployeeRepository bankEmployeeRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerMessageRepository customerMessageRepository;
    private final PanelMessageRepository panelMessageRepository;
    private final AuditLogService auditLogService;
    private final AccountService accountService;
    private final com.financeapp.service.PanelLockoutService panelLockoutService;
    private final org.springframework.cache.CacheManager cacheManager;

    public TreasuryController(BankVaultService bankVaultService,
                              LoanApplicationRepository loanApplicationRepository,
                              CustomerInquiryRepository customerInquiryRepository,
                              BankEmployeeRepository bankEmployeeRepository,
                              AccountRepository accountRepository,
                              UserRepository userRepository,
                              TransactionRepository transactionRepository,
                              CustomerMessageRepository customerMessageRepository,
                              PanelMessageRepository panelMessageRepository,
                              AuditLogService auditLogService,
                              AccountService accountService,
                              com.financeapp.service.PanelLockoutService panelLockoutService,
                              org.springframework.cache.CacheManager cacheManager) {
        this.bankVaultService = bankVaultService;
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerInquiryRepository = customerInquiryRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.customerMessageRepository = customerMessageRepository;
        this.panelMessageRepository = panelMessageRepository;
        this.auditLogService = auditLogService;
        this.accountService = accountService;
        this.panelLockoutService = panelLockoutService;
        this.cacheManager = cacheManager;
    }

    private void evictAccountCaches() {
        if (cacheManager == null) return;
        for (String cacheName : List.of("accounts", "accountById", "accountByNumber", "adminAccounts", "accountsByStatus")) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private boolean isTreasuryAuthenticated(HttpSession session) {
        Boolean auth = (Boolean) session.getAttribute(SESSION_KEY);
        return Boolean.TRUE.equals(auth);
    }

    private boolean isAuthorizedForTreasury(Principal principal) {
        if (principal == null) {
            return false;
        }
        User pUser = userRepository.findFirstByUsernameIgnoreCase(principal.getName()).orElse(null);
        if (pUser != null && pUser.getRole() != null && pUser.getRole().toUpperCase().contains("ADMIN")) {
            return true;
        }
        java.util.Optional<BankEmployee> empOpt = bankEmployeeRepository.findByUsername(principal.getName());
        if (empOpt.isEmpty()) {
            return false;
        }
        BankEmployee emp = empOpt.get();
        if (!"APPROVED".equalsIgnoreCase(emp.getApprovalStatus()) || "BLOCKED".equalsIgnoreCase(emp.getAccountStatus())) {
            return false;
        }
        String dept = emp.getDepartment() != null ? emp.getDepartment().trim().toLowerCase() : "";
        String pos = emp.getPosition() != null ? emp.getPosition().trim().toLowerCase() : "";
        return dept.contains("treasury") || dept.contains("xəzinə") || dept.contains("xaznadar")
                || pos.contains("treasury") || pos.contains("xəzinə") || pos.contains("xaznadar");
    }

    @GetMapping("/login")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String showTreasuryLogin(HttpSession session, Principal principal) {
        if (!isAuthorizedForTreasury(principal)) {
            return "redirect:/employee/portal";
        }
        if (isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury";
        }
        return "treasury-login";
    }

    @PostMapping({"/login", "/verify"})
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String verifyTreasuryCode(@RequestParam("treasuryCode") String treasuryCode,
                                     HttpSession session,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        if (!isAuthorizedForTreasury(principal)) {
            return "redirect:/employee/portal";
        }
        LocalDateTime lockedUntil = panelLockoutService.getActiveLockUntil("TREASURY");
        if (lockedUntil != null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Çoxsaylı yanlış cəhd səbəbilə Xəzinədar Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                            + lockedUntil.toString().substring(11, 16));
            return "redirect:/admin/treasury/login";
        }
        String clean = treasuryCode != null ? treasuryCode.trim() : "";
        String uname = principal != null ? principal.getName() : "Xəzinədar";
        if (TREASURY_CODE.equals(clean)) {
            panelLockoutService.resetFailures("TREASURY");
            session.setAttribute(SESSION_KEY, true);
            auditLogService.logAction(uname, "TREASURY_LOGIN_SUCCESS", "AUTH", "SESSION",
                    "Xəzinədar panelinə giriş təsdiqləndi.");
            redirectAttributes.addFlashAttribute("successMessage",
                    "Xəzinədar şöbəsinə xoş gəlmisiniz!");
            return "redirect:/admin/treasury";
        }
        int attempts = panelLockoutService.recordFailedAttempt("TREASURY");
        auditLogService.logAction(uname, "TREASURY_LOGIN_FAILED", "AUTH", "ATTEMPT_" + attempts,
                "Yanlış Xəzinədar giriş kodu");
        if (attempts >= com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS) {
            LocalDateTime until = panelLockoutService.getActiveLockUntil("TREASURY");
            String untilStr = until != null ? until.toString().substring(11, 16) : "";
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ 5 dəfə yanlış kod daxil edildiyi üçün Xəzinədar Paneli 15 dəqiqəlik kilidə alındı. Zəhmət olmasa gözləyin: " + untilStr);
        } else {
            int remaining = com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS - attempts;
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Yanlış Xəzinədar giriş kodu! (Qalan cəhd sayı: " + remaining + ").");
        }
        return "redirect:/admin/treasury/login";
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.POST, RequestMethod.GET})
    public String logoutTreasury(HttpServletRequest request,
                                 HttpServletResponse response,
                                 HttpSession session,
                                 Authentication authentication) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority())
                        || "ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (!isAdmin && authentication != null && authentication.getName() != null) {
            User pUser = userRepository.findFirstByUsernameIgnoreCase(authentication.getName()).orElse(null);
            isAdmin = pUser != null && pUser.getRole() != null && pUser.getRole().toUpperCase().contains("ADMIN");
        }
        if (isAdmin) {
            return "redirect:/admin";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        Cookie jsessionCookie = new Cookie("JSESSIONID", "");
        jsessionCookie.setPath("/");
        jsessionCookie.setMaxAge(0);
        response.addCookie(jsessionCookie);
        return "redirect:/login?logout=true";
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String showTreasuryDashboard(HttpSession session, Principal principal, Model model) {
        if (!isAuthorizedForTreasury(principal)) {
            return "redirect:/employee/portal";
        }
        if (principal != null) {
            User pUser = userRepository.findFirstByUsernameIgnoreCase(principal.getName()).orElse(null);
            model.addAttribute("isAdminUser", pUser != null && pUser.getRole() != null && pUser.getRole().toUpperCase().contains("ADMIN"));
        } else {
            model.addAttribute("isAdminUser", false);
        }

        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }

        BigDecimal vaultBalance = bankVaultService.getVaultBalance();
        List<VaultTransaction> vaultTransactions = bankVaultService.getAllTransactions();
        if (vaultTransactions == null) vaultTransactions = Collections.emptyList();

        BigDecimal inflowTotal = vaultTransactions.stream()
                .filter(t -> "INFLOW".equalsIgnoreCase(t.getTransactionType()))
                .map(VaultTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outflowTotal = vaultTransactions.stream()
                .filter(t -> "OUTFLOW".equalsIgnoreCase(t.getTransactionType()))
                .map(VaultTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal commissionTotal = vaultTransactions.stream()
                .filter(t -> {
                    String cat = t.getCategory() != null ? t.getCategory().toUpperCase() : "";
                    return cat.contains("COMMISSION") || cat.contains("FEE") || cat.contains("INTEREST");
                })
                .map(VaultTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal corporateTotal = vaultTransactions.stream()
                .filter(t -> {
                    String cat = t.getCategory() != null ? t.getCategory().toUpperCase() : "";
                    return cat.contains("CORPORATE") || cat.contains("COMPANY");
                })
                .map(VaultTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LoanApplication> treasuryLoans = loanApplicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(l -> "SENT_TO_TREASURY".equalsIgnoreCase(l.getStatus()))
                .toList();

        List<BankEmployee> employees = bankEmployeeRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> "APPROVED".equalsIgnoreCase(e.getApprovalStatus())
                        && !"BLOCKED".equalsIgnoreCase(e.getAccountStatus()))
                .toList();
        List<PanelMessage> panelMessages = panelMessageRepository.findByFromPanelOrToPanelOrderByCreatedAtDesc("TREASURY", "TREASURY");
        List<AccountResponse> allAccounts = accountService.getAllAccountsForAdmin();

        model.addAttribute("vaultBalanceFormatted", String.format("€%,.2f", vaultBalance));
        model.addAttribute("vaultBalanceAzn", String.format("₼%,.2f", com.financeapp.service.CurrencyConverter.convert(vaultBalance, "EUR", "AZN")));
        model.addAttribute("vaultBalanceUsd", String.format("$%,.2f", com.financeapp.service.CurrencyConverter.convert(vaultBalance, "EUR", "USD")));
        model.addAttribute("inflowTotalFormatted", String.format("€%,.2f", inflowTotal));
        model.addAttribute("outflowTotalFormatted", String.format("€%,.2f", outflowTotal));
        model.addAttribute("commissionTotalFormatted", String.format("€%,.2f", commissionTotal));
        model.addAttribute("corporateTotalFormatted", String.format("€%,.2f", corporateTotal));
        model.addAttribute("vaultTransactions", vaultTransactions);
        model.addAttribute("allAccounts", allAccounts != null ? allAccounts : Collections.emptyList());
        model.addAttribute("treasuryLoans", treasuryLoans);
        model.addAttribute("employees", employees);
        model.addAttribute("panelMessages", panelMessages);
        model.addAttribute("treasuryUsername", principal != null ? principal.getName() : "Xəzinədar");

        return "treasury";
    }

    @PostMapping("/vault/credit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String issueCreditFromTreasury(@RequestParam("accountNumber") String accountNumber,
                                          @RequestParam("amount") BigDecimal amount,
                                          @RequestParam(value = "currency", defaultValue = "AZN") String currency,
                                          @RequestParam(value = "interestRate", required = false, defaultValue = "12.0") BigDecimal interestRate,
                                          @RequestParam(value = "note", required = false) String note,
                                          HttpSession session,
                                          Principal principal,
                                          RedirectAttributes redirectAttributes) {
        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }
        try {
            String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
            String sym = com.financeapp.service.CurrencyConverter.symbol(normCur);
            BigDecimal rate = (interestRate != null && interestRate.compareTo(BigDecimal.ZERO) >= 0)
                    ? interestRate : new BigDecimal("12.0");
            BigDecimal interestAmount = amount.multiply(rate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal totalRepayment = amount.add(interestAmount);

            bankVaultService.issueCustomerCredit(accountNumber, amount, normCur, rate, note);
            evictAccountCaches();

            String actor = principal != null ? principal.getName() : "Xəzinədar";
            auditLogService.logAction(actor, "TREASURY_ISSUE_CREDIT", "ACCOUNT", accountNumber,
                    String.format("Xəzinədar tərəfindən kredit/likvidlik ayrıldı: %s%,.2f %s, Hesab: %s",
                            sym, amount, normCur, accountNumber));

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Xəzinədən vəsait köçürüldü: %s%,.2f %s | Faiz: %s%% (%s%,.2f) | Ümumi Geri Ödəniş: %s%,.2f (Hesab: %s)",
                            sym, amount, normCur, rate, sym, interestAmount, sym, totalRepayment, accountNumber));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kredit verilməsi xətası: " + e.getMessage());
        }
        return "redirect:/admin/treasury";
    }

    @PostMapping("/vault/inflow")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String addTreasuryInflow(@RequestParam("amount") BigDecimal amount,
                                    @RequestParam(value = "currency", defaultValue = "AZN") String currency,
                                    @RequestParam("source") String source,
                                    @RequestParam(value = "category", defaultValue = "CORPORATE_TRANSFER") String category,
                                    HttpSession session,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }
        try {
            String actor = principal != null ? principal.getName() : "Xəzinədar";
            String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
            String sym = com.financeapp.service.CurrencyConverter.symbol(normCur);
            BigDecimal eurAmount = com.financeapp.service.CurrencyConverter.convert(amount, normCur, "EUR");
            bankVaultService.recordVaultInflow(eurAmount, amount, normCur, source, category + " (" + sym + amount + " " + normCur + ") — " + actor);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("%s%,.2f %s məbləğində vəsait Xəzinəyə (%s) uğurla daxil edildi.", sym, amount, normCur, category));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəzinə əməliyyatı xətası: " + e.getMessage());
        }
        return "redirect:/admin/treasury";
    }

    @PostMapping("/pay-salary/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String payEmployeeSalaryFromTreasury(@PathVariable("id") Long id,
                                                @RequestParam(value = "amount", required = false) BigDecimal customAmount,
                                                @RequestParam(value = "currency", defaultValue = "AZN") String currency,
                                                HttpSession session,
                                                Principal principal,
                                                RedirectAttributes redirectAttributes) {
        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı."));
            BigDecimal payAmt = (customAmount != null && customAmount.compareTo(BigDecimal.ZERO) > 0)
                    ? customAmount
                    : (emp.getSalary() != null && emp.getSalary().compareTo(BigDecimal.ZERO) > 0
                    ? emp.getSalary() : new BigDecimal("1500.00"));
            String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
            String sym = com.financeapp.service.CurrencyConverter.symbol(normCur);
            BigDecimal eurPayAmt = com.financeapp.service.CurrencyConverter.convert(payAmt, normCur, "EUR");

            String actor = principal != null ? principal.getName() : "Xəzinədar";
            bankVaultService.recordWithdrawal(eurPayAmt, payAmt, normCur,
                    emp.getFullName(),
                    emp.getEmployeeId() != null ? emp.getEmployeeId() : emp.getUsername(),
                    "Maaş Ödənişi (" + sym + payAmt + " " + normCur + ", Xəzinədar: " + actor + ")");

            emp.setSalary(payAmt);
            emp.setSalaryCurrency(normCur);
            emp.setLastSalaryPaidAt(LocalDateTime.now());
            User empUser = userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).orElse(null);
            if (empUser != null) {
                Account empAcc = accountRepository.findFirstByUser(empUser).orElse(null);
                if (empAcc == null) {
                    empAcc = new Account();
                    empAcc.setAccountNumber(emp.getAccountNumber() != null && !emp.getAccountNumber().isBlank()
                            ? emp.getAccountNumber()
                            : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                    empAcc.setBalance(BigDecimal.ZERO);
                    empAcc.setCurrency(normCur);
                    empAcc.setAccountType("SALARY");
                    empAcc.setStatus(AccountStatus.ACTIVE);
                    empAcc.setUser(empUser);
                }
                String oldCur = empAcc.getCurrency() != null && !empAcc.getCurrency().trim().isEmpty()
                        ? com.financeapp.service.CurrencyConverter.normalize(empAcc.getCurrency()) : normCur;
                BigDecimal currentBal = empAcc.getBalance() != null ? empAcc.getBalance() : BigDecimal.ZERO;
                if (!oldCur.equals(normCur) && currentBal.compareTo(BigDecimal.ZERO) > 0) {
                    currentBal = com.financeapp.service.CurrencyConverter.convert(currentBal, oldCur, normCur);
                }
                // Seçilən valyuta (AZN, USD, EUR) birbaşa işçinin hesab valyutası və məbləği kimi köçürülür
                empAcc.setCurrency(normCur);
                empAcc.setBalance(currentBal.add(payAmt));
                accountRepository.saveAndFlush(empAcc);
                evictAccountCaches();
                emp.setAccountNumber(empAcc.getAccountNumber());
                emp.setBalance(empAcc.getBalance());

                Transaction tx = new Transaction();
                tx.setAccount(empAcc);
                tx.setUser(empUser);
                tx.setAmount(payAmt);
                tx.setCurrency(normCur);
                tx.setType("DEPOSIT");
                tx.setReferenceNumber("TX-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-SAL");
                tx.setDescription("Xəzinədar Maaş Ödənişi (" + emp.getEmployeeId() + ": " + sym + payAmt + " " + normCur + ")");
                tx.setTimestamp(LocalDateTime.now());
                transactionRepository.save(tx);
            }
            bankEmployeeRepository.save(emp);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("%s əməkdaşına %s%,.2f %s maaş Xəzinədən hesabına köçürüldü.", emp.getFullName(), sym, payAmt, normCur));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Maaş ödənişi xətası: " + e.getMessage());
        }
        return "redirect:/admin/treasury";
    }

    @PostMapping("/loans/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String disburseLoanFromTreasury(@RequestParam("loanId") Long loanId,
                                             @RequestParam(value = "currency", defaultValue = "AZN") String currency,
                                             HttpSession session,
                                             Principal principal,
                                             RedirectAttributes redirectAttributes) {
        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }
        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı."));
            if ("APPROVED".equalsIgnoreCase(loan.getStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bu kredit artıq rəsmiləşdirilib.");
                return "redirect:/admin/treasury";
            }

            String normCur = com.financeapp.service.CurrencyConverter.normalize(currency);
            String sym = com.financeapp.service.CurrencyConverter.symbol(normCur);
            BigDecimal eurLoanAmt = com.financeapp.service.CurrencyConverter.convert(loan.getAmount(), normCur, "EUR");

            String actor = principal != null ? principal.getName() : "Xəzinədar";
            loan.setStatus("APPROVED");
            loan.setApprovedBy(actor);
            loan.setApprovedAt(LocalDateTime.now());
            loanApplicationRepository.save(loan);

            User user = userRepository.findFirstByUsernameIgnoreCase(loan.getUsername()).orElse(null);
            Account targetAcc = null;
            if (loan.getAccountNumber() != null && !loan.getAccountNumber().isEmpty()) {
                targetAcc = accountRepository.findByAccountNumber(loan.getAccountNumber()).orElse(null);
            }
            if (targetAcc == null && user != null) {
                targetAcc = accountRepository.findFirstByUser(user).orElse(null);
            }
            if (targetAcc != null) {
                String oldCur = targetAcc.getCurrency() != null && !targetAcc.getCurrency().trim().isEmpty()
                        ? com.financeapp.service.CurrencyConverter.normalize(targetAcc.getCurrency()) : normCur;
                BigDecimal current = targetAcc.getBalance() != null ? targetAcc.getBalance() : BigDecimal.ZERO;
                if (!oldCur.equals(normCur) && current.compareTo(BigDecimal.ZERO) > 0) {
                    current = com.financeapp.service.CurrencyConverter.convert(current, oldCur, normCur);
                }
                targetAcc.setCurrency(normCur);
                targetAcc.setBalance(current.add(loan.getAmount()));
                targetAcc.setStatus(AccountStatus.ACTIVE);
                accountRepository.saveAndFlush(targetAcc);
                evictAccountCaches();

                Transaction tx = new Transaction();
                tx.setAccount(targetAcc);
                tx.setUser(targetAcc.getUser() != null ? targetAcc.getUser() : user);
                tx.setAmount(loan.getAmount());
                tx.setCurrency(normCur);
                tx.setType("DEPOSIT");
                tx.setReferenceNumber("TX-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-LN");
                tx.setDescription("Kredit Rəsmiləşdirilməsi (Xəzinədar • " + sym + loan.getAmount() + " " + normCur + ")");
                tx.setTimestamp(LocalDateTime.now());
                transactionRepository.save(tx);
            }

            bankVaultService.recordWithdrawal(eurLoanAmt, loan.getAmount(), normCur,
                    loan.getCustomerName(),
                    targetAcc != null ? targetAcc.getAccountNumber() : loan.getAccountNumber(),
                    "Kredit Ayırması (" + sym + loan.getAmount() + " " + normCur + ", Xəzinədar: " + actor + ")");

            customerInquiryRepository.findAll().stream()
                    .filter(i -> loan.getId().equals(i.getLoanApplicationId()))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Kredit müraciətiniz Sorğu, Security və Xəzinədar mərhələlərindən uğurla keçdi və məbləğ hesabınıza köçürüldü.");
                        inq.setRepliedBy(actor);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });

            CustomerMessage msg = new CustomerMessage(
                    loan.getUsername(),
                    actor,
                    "Kreditiniz Rəsmiləşdirildi",
                    String.format("Hörmətli %s, %s%,.2f %s məbləğində kreditiniz Xəzinədar şöbəsi tərəfindən rəsmiləşdirildi və hesabınıza köçürüldü.",
                            loan.getCustomerName(), sym, loan.getAmount(), normCur),
                    "SUCCESS"
            );
            customerMessageRepository.save(msg);

            auditLogService.logAction(actor, "TREASURY_LOAN_APPROVED", "LOAN", loanId.toString(),
                    "Kredit Xəzinədar tərəfindən rəsmiləşdirildi: " + sym + loan.getAmount() + " " + normCur);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Kredit (#%d) rəsmiləşdirildi və %s%,.2f %s müştərinin hesabına köçürüldü.", loanId, sym, loan.getAmount(), normCur));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kredit rəsmiləşdirmə xətası: " + e.getMessage());
        }
        return "redirect:/admin/treasury";
    }

    @PostMapping("/loans/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String rejectLoanFromTreasury(@RequestParam("loanId") Long loanId,
                                         @RequestParam(value = "reason", defaultValue = "Xəzinədar tərəfindən rədd edildi") String reason,
                                         HttpSession session,
                                         Principal principal,
                                         RedirectAttributes redirectAttributes) {
        if (!isTreasuryAuthenticated(session)) {
            return "redirect:/admin/treasury/login";
        }
        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı."));
            String actor = principal != null ? principal.getName() : "Xəzinədar";
            loan.setStatus("REJECTED");
            loan.setApprovedBy(actor);
            loan.setAdminNote(reason);
            loanApplicationRepository.save(loan);

            customerInquiryRepository.findAll().stream()
                    .filter(i -> loan.getId().equals(i.getLoanApplicationId()))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Kredit müraciətinə imtina verildi: " + reason);
                        inq.setRepliedBy(actor);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });

            redirectAttributes.addFlashAttribute("successMessage", "Kredit müraciəti (#" + loanId + ") rədd edildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəta: " + e.getMessage());
        }
        return "redirect:/admin/treasury";
    }
}
