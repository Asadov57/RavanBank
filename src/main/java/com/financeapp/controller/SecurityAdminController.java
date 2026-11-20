package com.financeapp.controller;

import com.financeapp.enums.AccountStatus;
import com.financeapp.model.*;
import com.financeapp.repository.*;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class SecurityAdminController {

    private static final Logger log = LoggerFactory.getLogger(SecurityAdminController.class);

    private final SecurityAppealRepository securityAppealRepository;
    private final SecurityRegistrationRepository securityRegistrationRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final CustomerMessageRepository customerMessageRepository;
    private final ArchivedAccountRepository archivedAccountRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LoanApplicationRepository loanApplicationRepository;
    private final BankEmployeeRepository bankEmployeeRepository;
    private final CustomerInquiryRepository customerInquiryRepository;
    private final PanelMessageRepository panelMessageRepository;
    private final com.financeapp.service.DepartmentQuotaService departmentQuotaService;
    private final com.financeapp.service.PanelLockoutService panelLockoutService;
    private final com.financeapp.service.PendingAccountCleanupService pendingAccountCleanupService;

    public SecurityAdminController(SecurityAppealRepository securityAppealRepository,
                                   SecurityRegistrationRepository securityRegistrationRepository,
                                   SecurityNotificationRepository securityNotificationRepository,
                                   CustomerMessageRepository customerMessageRepository,
                                   ArchivedAccountRepository archivedAccountRepository,
                                   UserRepository userRepository,
                                   AccountRepository accountRepository,
                                   SimpMessagingTemplate messagingTemplate,
                                   LoanApplicationRepository loanApplicationRepository,
                                   BankEmployeeRepository bankEmployeeRepository,
                                   CustomerInquiryRepository customerInquiryRepository,
                                   PanelMessageRepository panelMessageRepository,
                                   com.financeapp.service.DepartmentQuotaService departmentQuotaService,
                                   com.financeapp.service.PanelLockoutService panelLockoutService,
                                   com.financeapp.service.PendingAccountCleanupService pendingAccountCleanupService) {
        this.securityAppealRepository = securityAppealRepository;
        this.securityRegistrationRepository = securityRegistrationRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.customerMessageRepository = customerMessageRepository;
        this.archivedAccountRepository = archivedAccountRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.messagingTemplate = messagingTemplate;
        this.loanApplicationRepository = loanApplicationRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.customerInquiryRepository = customerInquiryRepository;
        this.panelMessageRepository = panelMessageRepository;
        this.departmentQuotaService = departmentQuotaService;
        this.panelLockoutService = panelLockoutService;
        this.pendingAccountCleanupService = pendingAccountCleanupService;
    }

    @PostMapping("/appeals/submit")
    public String submitCustomerAppeal(
            @RequestParam("category") String category,
            @RequestParam("subject") String subject,
            @RequestParam("message") String message,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "redirectTo", required = false) String redirectTo,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        String referer = httpRequest != null ? httpRequest.getHeader("Referer") : null;
        String targetRedirect = "redirect:/dashboard#feedbackSection";
        if ("/card".equals(redirectTo) || (referer != null && referer.contains("/card"))) {
            targetRedirect = "redirect:/card#cardFeedbackSection";
        } else if ("/employee/portal".equals(redirectTo) || (referer != null && referer.contains("/employee/portal"))) {
            targetRedirect = "redirect:/employee/portal";
        }

        if (principal == null) {
            return "redirect:/login";
        }

        try {
            String username = principal.getName();
            User user = userRepository.findFirstByUsernameIgnoreCase(username)
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(username))
                    .orElse(null);

            String canonicalUsername = user != null ? user.getUsername() : username;
            String customerName = user != null && user.getFullName() != null && !user.getFullName().trim().isEmpty()
                    ? user.getFullName() : canonicalUsername;
            String contactPhone = (phoneNumber != null && !phoneNumber.trim().isEmpty())
                    ? phoneNumber.trim()
                    : (user != null && user.getEmail() != null ? user.getEmail() : (user != null && user.getPhoneNumber() != null ? user.getPhoneNumber() : ""));

            String accNo = "N/A";
            if (user != null && user.getAccounts() != null && !user.getAccounts().isEmpty()) {
                accNo = user.getAccounts().get(0).getAccountNumber();
            }

            String rawCat = category != null ? category.trim().toUpperCase() : "COMPLAINT";
            String normalizedCategory = "COMPLAINT";
            if (rawCat.contains("ŞİKAYƏT") || rawCat.contains("COMPLAINT")) normalizedCategory = "COMPLAINT";
            else if (rawCat.contains("TƏKLİF") || rawCat.contains("SUGGEST")) normalizedCategory = "SUGGESTION";
            else if (rawCat.contains("İRAD") || rawCat.contains("RƏY") || rawCat.contains("FEEDBACK")) normalizedCategory = "FEEDBACK";
            else if (rawCat.contains("TƏHLÜKƏ") || rawCat.contains("SECURITY")) normalizedCategory = "SECURITY";

            SecurityAppeal appeal = new SecurityAppeal(
                    canonicalUsername,
                    customerName,
                    accNo,
                    contactPhone,
                    normalizedCategory,
                    subject != null ? subject.trim() : "General Inquiry",
                    message != null ? message.trim() : ""
            );
            appeal.setStatus("NEW");
            appeal.setTargetDepartment("INQUIRY");
            securityAppealRepository.save(appeal);

            CustomerInquiry inquiry = new CustomerInquiry();
            inquiry.setUsername(canonicalUsername);
            inquiry.setCustomerName(customerName);
            inquiry.setPhoneNumber(contactPhone);
            if (user != null && user.getEmail() != null) {
                inquiry.setEmail(user.getEmail());
            }
            String mappedType = "COMPLAINT";
            if ("SUGGESTION".equals(normalizedCategory) || "FEEDBACK".equals(normalizedCategory)) {
                mappedType = "INQUIRY";
            } else if ("SECURITY".equals(normalizedCategory)) {
                mappedType = "SERVICE_ISSUE";
            }
            inquiry.setInquiryType(mappedType);
            inquiry.setSubject("[" + normalizedCategory + "] " + (subject != null ? subject.trim() : "Müraciət"));
            inquiry.setMessage(message != null ? message.trim() : "");
            inquiry.setReferenceNo("INQ-" + System.currentTimeMillis() % 1000000);
            inquiry.setStatus("NEW");
            customerInquiryRepository.save(inquiry);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Müraciətiniz qeydə alındı və birbaşa Bank Təhlükəsizlik Mərkəzinə göndərildi. Təşəkkür edirik!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Müraciət göndərilərkən xəta: " + e.getMessage());
        }

        return targetRedirect;
    }

    @GetMapping("/admin/security")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'SECURITY', 'ROLE_SECURITY')")
    public String showSecurityPage(HttpSession session, Model model, Principal principal) {
        if (principal != null) {
            Optional<BankEmployee> empOpt = bankEmployeeRepository.findByUsername(principal.getName());
            if (empOpt.isPresent()) {
                BankEmployee emp = empOpt.get();
                if (!"Security".equalsIgnoreCase(emp.getDepartment())) {
                    SecurityNotification breachNotif = new SecurityNotification(
                            "🚨 Unauthorized Security Access Attempt",
                            String.format("Inquiry staff member '%s' (%s, ID: %s) attempted direct access to Security Center! Access blocked.",
                                    emp.getUsername(), emp.getFullName(), emp.getEmployeeId()),
                            "UNAUTHORIZED_SECURITY_CODE",
                            emp.getUsername()
                    );
                    securityNotificationRepository.save(breachNotif);
                    return "redirect:/employee/portal?accessDenied=true";
                }
            }
        }

        Boolean isVerified = (Boolean) session.getAttribute("SECURITY_AUTHENTICATED");

        if (isVerified == null || !isVerified) {
            model.addAttribute("needsAuth", true);
            model.addAttribute("adminUsername", principal != null ? principal.getName() : "Ravan Asadov");
            return "security";
        }

        boolean isAdminUser = false;
        if (principal != null) {
            User pUser = userRepository.findFirstByUsernameIgnoreCase(principal.getName()).orElse(null);
            isAdminUser = pUser != null && pUser.getRole() != null && pUser.getRole().toUpperCase().contains("ADMIN");
        }
        model.addAttribute("isAdminUser", isAdminUser);

        pendingAccountCleanupService.processPendingAccountsLifecycle();

        List<SecurityAppeal> appeals = Collections.emptyList();
        List<PanelMessage> panelMessages = panelMessageRepository.findByFromPanelOrToPanelOrderByCreatedAtDesc("SECURITY", "SECURITY");
        model.addAttribute("panelMessages", panelMessages);
        List<SecurityRegistration> registrations = securityRegistrationRepository.findAllByOrderByCreatedAtDesc();
        List<SecurityNotification> notifications = securityNotificationRepository.findTop50ByOrderByCreatedAtDesc();
        List<CustomerMessage> sentMessages = customerMessageRepository.findAllByOrderByCreatedAtDesc();

        List<User> customerUsers = userRepository.findAll().stream()
                .filter(u -> !"ROLE_ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();

        List<Account> accounts = accountRepository.findAll();

        long newAppealsCount = securityAppealRepository.countByStatus("NEW");
        long totalAppealsCount = appeals.size();
        long verifiedRegsCount = registrations.stream().filter(r -> "VERIFIED".equalsIgnoreCase(r.getStatus())).count();
        long archivedCount = archivedAccountRepository.count();
        List<LoanApplication> loanApplications = loanApplicationRepository.findAllByOrderByCreatedAtDesc();
        long pendingLoansCount = loanApplicationRepository.countByStatus("PENDING");
        List<BankEmployee> employees = bankEmployeeRepository.findAllByOrderByCreatedAtDesc();
        for (BankEmployee emp : employees) {
            userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).ifPresent(empUser -> {
                com.financeapp.model.Account empAcc = accountRepository.findFirstByUser(empUser).orElseGet(() -> {
                    com.financeapp.model.Account acc = new com.financeapp.model.Account();
                    acc.setAccountNumber(emp.getAccountNumber() != null && !emp.getAccountNumber().isEmpty()
                            ? emp.getAccountNumber()
                            : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                    acc.setBalance(emp.getBalance() != null ? emp.getBalance() : BigDecimal.ZERO);
                    acc.setCurrency("EUR");
                    acc.setAccountType("SALARY");
                    acc.setStatus("ACTIVE".equalsIgnoreCase(emp.getAccountStatus()) ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION);
                    acc.setUser(empUser);
                    return accountRepository.save(acc);
                });
                emp.setAccountNumber(empAcc.getAccountNumber());
                emp.setBalance(empAcc.getBalance() != null ? empAcc.getBalance() : BigDecimal.ZERO);
                bankEmployeeRepository.save(emp);
            });
            if (emp.getAccountNumber() == null || emp.getAccountNumber().isEmpty()) {
                emp.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                bankEmployeeRepository.save(emp);
            }
        }
        long pendingEmployeesCount = bankEmployeeRepository.countByApprovalStatus("SENT_TO_SECURITY");
        departmentQuotaService.populateQuotaAttributes(model);
        if (principal != null) {
            bankEmployeeRepository.findByUsername(principal.getName()).ifPresent(loggedEmp -> {
                model.addAttribute("loggedInEmployee", loggedEmp);
            });
        }

        model.addAttribute("needsAuth", false);
        model.addAttribute("appeals", appeals);
        model.addAttribute("registrations", registrations);
        model.addAttribute("notifications", notifications);
        model.addAttribute("customerUsers", customerUsers);
        model.addAttribute("accounts", accounts);
        model.addAttribute("sentMessages", sentMessages);
        model.addAttribute("loanApplications", loanApplications);
        model.addAttribute("pendingLoansCount", pendingLoansCount);
        model.addAttribute("employees", employees);
        model.addAttribute("pendingEmployeesCount", pendingEmployeesCount);
        model.addAttribute("newAppealsCount", newAppealsCount);
        model.addAttribute("totalAppealsCount", totalAppealsCount);
        model.addAttribute("verifiedRegsCount", verifiedRegsCount);
        model.addAttribute("archivedCount", archivedCount);
        model.addAttribute("adminUsername", principal != null ? principal.getName() : "Ravan Asadov");

        return "security";
    }

    @PostMapping("/admin/security/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'SECURITY', 'ROLE_SECURITY')")
    public Object verifySecurityCode(
            @RequestParam("securityCode") String securityCode,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);

        if (principal != null) {
            Optional<BankEmployee> empOpt = bankEmployeeRepository.findByUsername(principal.getName());
            if (empOpt.isPresent() && !"Security".equalsIgnoreCase(empOpt.get().getDepartment())) {
                BankEmployee emp = empOpt.get();
                SecurityNotification notif = new SecurityNotification(
                        "🚨 CRITICAL: Unauthorized Security Code Attempt",
                        "Inquiry staff member '" + emp.getUsername() + "' attempted to verify Security code 'RavanBank'. Blocked.",
                        "UNAUTHORIZED_SECURITY_CODE",
                        emp.getUsername()
                );
                securityNotificationRepository.save(notif);

                String msg = "GİRİŞ QADAĞANDIR: Siz Sorğu şöbəsinə aidsiniz və Təhlükəsizlik Giriş Kodunu istifadə edə bilməzsiniz.";
                if (isAjax) {
                    return ResponseEntity.status(403).body(Map.of("success", false, "message", msg));
                }
                redirectAttributes.addFlashAttribute("errorMessage", msg);
                return "redirect:/employee/portal";
            }
        }

        LocalDateTime lockedUntil = panelLockoutService.getActiveLockUntil("SECURITY");
        if (lockedUntil != null) {
            String lockMsg = "❌ Çoxsaylı yanlış cəhd səbəbilə Security Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                    + lockedUntil.toString().substring(11, 16);
            if (isAjax) {
                return ResponseEntity.status(429).body(Map.of("success", false, "message", lockMsg));
            }
            redirectAttributes.addFlashAttribute("errorMessage", lockMsg);
            return "redirect:/admin/security";
        }

        String code = securityCode != null ? securityCode.trim() : "";
        if ("RavanBank".equalsIgnoreCase(code)) {
            panelLockoutService.resetFailures("SECURITY");
            session.setAttribute("SECURITY_AUTHENTICATED", true);
            if (isAjax) {
                return ResponseEntity.ok(Map.of("success", true, "redirect", "/admin/security"));
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Təhlükəsizlik girişi təsdiqləndi! Təhlükəsizlik Mərkəzinə xoş gəlmisiniz.");
        } else {
            session.removeAttribute("SECURITY_AUTHENTICATED");
            int attempts = panelLockoutService.recordFailedAttempt("SECURITY");
            String msg;
            if (attempts >= com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS) {
                LocalDateTime until = panelLockoutService.getActiveLockUntil("SECURITY");
                String untilStr = until != null ? until.toString().substring(11, 16) : "";
                msg = "❌ 5 dəfə yanlış təhlükəsizlik kodu daxil edildiyi üçün Security Paneli 15 dəqiqəlik kilidə alındı. Zəhmət olmasa gözləyin: " + untilStr;
            } else {
                int remaining = com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS - attempts;
                msg = "Yanlış Təhlükəsizlik Kodu! Giriş qadağandır (Qalan cəhd sayı: " + remaining + ").";
            }
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg));
            }
            redirectAttributes.addFlashAttribute("errorMessage", msg);
        }

        return "redirect:/admin/security";
    }

    @PostMapping("/admin/security/logout")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'SECURITY', 'ROLE_SECURITY')")
    public String logoutSecurity(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute("SECURITY_AUTHENTICATED");
        redirectAttributes.addFlashAttribute("successMessage", "Successfully logged out from Security / Təhlükəsizlik portal.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/security/appeals/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public Object updateAppealStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status,
            @RequestParam(value = "adminNote", required = false) String adminNote,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Boolean isVerified = (Boolean) session.getAttribute("SECURITY_AUTHENTICATED");
        if (isVerified == null || !isVerified) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
            }
            return "redirect:/admin/security";
        }

        SecurityAppeal appeal = securityAppealRepository.findById(id).orElse(null);
        if (appeal != null) {
            appeal.setStatus(status != null && !status.trim().isEmpty() ? status.trim() : "REVIEWED");
            if (adminNote != null && !adminNote.trim().isEmpty()) {
                appeal.setAdminNote(adminNote.trim());

                String recipient = resolveCanonicalUsername(appeal.getUsername(), appeal.getCustomerName());

                String replyTitle = "Müraciətinizə Rəy: " + (appeal.getSubject() != null ? appeal.getSubject() : "Müraciət");
                CustomerMessage replyMsg = new CustomerMessage(
                        recipient,
                        "AsadovBank Təhlükəsizlik Xidməti",
                        replyTitle,
                        adminNote.trim(),
                        "APPEAL_REPLY"
                );
                customerMessageRepository.save(replyMsg);
            }
            securityAppealRepository.save(appeal);
            redirectAttributes.addFlashAttribute("successMessage", "Müraciət statusu yeniləndi və rəy müştəriyə çatdırıldı.");
        }

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            long newAppealsCount = securityAppealRepository.countByStatus("NEW");
            return ResponseEntity.ok(Map.of("success", true, "id", id, "status", status, "newAppealsCount", newAppealsCount));
        }

        return "redirect:/admin/security#appealsSection";
    }

    @PostMapping("/admin/security/appeals/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public Object replyToAppeal(
            @PathVariable("id") Long id,
            @RequestParam("adminReply") String adminReply,
            @RequestParam(value = "status", defaultValue = "REVIEWED") String status,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Boolean isVerified = (Boolean) session.getAttribute("SECURITY_AUTHENTICATED");
        if (isVerified == null || !isVerified) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
            }
            return "redirect:/admin/security";
        }

        if (adminReply == null || adminReply.trim().isEmpty()) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Rəy mətni boş ola bilməz."));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Rəy mətni boş ola bilməz.");
            return "redirect:/admin/security#appealsSection";
        }

        SecurityAppeal appeal = securityAppealRepository.findById(id).orElse(null);
        if (appeal == null) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Müraciət tapılmadı."));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Müraciət tapılmadı.");
            return "redirect:/admin/security#appealsSection";
        }

        appeal.setStatus(status != null && !status.trim().isEmpty() ? status.trim() : "REVIEWED");
        appeal.setAdminNote(adminReply.trim());
        securityAppealRepository.save(appeal);

        String replyTitle = "Müraciətinizə Rəsmi Cavab: " + (appeal.getSubject() != null ? appeal.getSubject() : "Müraciət");
        String recipient = resolveCanonicalUsername(appeal.getUsername(), appeal.getCustomerName());

        CustomerMessage replyMsg = new CustomerMessage(
                recipient,
                "AsadovBank Təhlükəsizlik Xidməti",
                replyTitle,
                adminReply.trim(),
                "APPEAL_REPLY"
        );
        customerMessageRepository.save(replyMsg);

        SecurityNotification notif = new SecurityNotification(
                "Müştəri Müraciətinə Cavab Verildi",
                String.format("✉️ @%s istifadəçisinin müraciətinə rəy göndərildi: '%s'", appeal.getUsername(), replyTitle),
                "APPEAL_REPLIED",
                appeal.getUsername()
        );
        securityNotificationRepository.save(notif);

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            long newAppealsCount = securityAppealRepository.countByStatus("NEW");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "status", appeal.getStatus(),
                    "adminNote", appeal.getAdminNote(),
                    "newAppealsCount", newAppealsCount,
                    "message", "Rəy müştərinin hesabına uğurla çatdırıldı!"
            ));
        }

        redirectAttributes.addFlashAttribute("successMessage", "Rəy müştərinin hesabına uğurla çatdırıldı!");
        return "redirect:/admin/security#appealsSection";
    }

    @PostMapping("/admin/security/appeals/manual-create")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public Object createManualAppeal(
            @RequestParam("customerName") String customerName,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam("category") String category,
            @RequestParam("subject") String subject,
            @RequestParam("message") String message,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            RedirectAttributes redirectAttributes) {

        String safeUsername = (username != null && !username.trim().isEmpty()) ? username.trim() : "manual-entry";
        String safeAccount = (accountNumber != null && !accountNumber.trim().isEmpty()) ? accountNumber.trim() : "N/A";
        String safePhone = (phoneNumber != null && !phoneNumber.trim().isEmpty()) ? phoneNumber.trim() : "admin-entry";

        SecurityAppeal appeal = new SecurityAppeal(
                customerName.trim(),
                safeUsername,
                safeAccount,
                safePhone,
                category.toUpperCase(),
                subject.trim(),
                message.trim()
        );
        appeal.setStatus("NEW");
        securityAppealRepository.save(appeal);

        SecurityNotification notif = new SecurityNotification(
                "Yeni Müraciət (Admin Qeydi)",
                String.format("✍️ Admin tərəfindən əl ilə yeni müştəri müraciəti qeydə alındı: %s - %s", customerName, subject),
                "APPEAL_NEW",
                safeUsername
        );
        securityNotificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception ignored) {}

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Customer inquiry successfully added.", "appealId", appeal.getId()));
        }

        redirectAttributes.addFlashAttribute("successMessage", "Müştəri müraciəti uğurla əlavə edildi.");
        return "redirect:/admin/security#appealsSection";
    }

    @PostMapping("/admin/accounts/{id}/toggle-freeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN')")
    public Object toggleFreezeAccount(@PathVariable("id") Long id,
                                      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Account not found"));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Hesab tapılmadı.");
            return "redirect:/admin/security";
        }

        String newStatus;
        if (account.getStatus() == AccountStatus.ACTIVE) {
            account.setStatus(AccountStatus.BLOCKED);
            newStatus = "BLOCKED";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Hesab uğurla donduruldu (BLOCKED): " + account.getAccountNumber());

            SecurityNotification notif = new SecurityNotification(
                    "Hesab Donduruldu",
                    "❄️ Hesab təhlükəsizlik məqsədilə donduruldu: " + account.getAccountNumber(),
                    "ACCOUNT_FROZEN",
                    account.getUser() != null ? account.getUser().getUsername() : ""
            );
            securityNotificationRepository.save(notif);
        } else {
            account.setStatus(AccountStatus.ACTIVE);
            newStatus = "ACTIVE";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Hesab yenidən aktivləşdirildi (ACTIVE): " + account.getAccountNumber());
        }

        accountRepository.save(account);

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "status", newStatus,
                    "accountNumber", account.getAccountNumber(),
                    "message", "ACTIVE".equals(newStatus) ? "Hesab aktivləşdirildi" : "Hesab donduruldu"
            ));
        }

        return "redirect:/admin/security#accountsSection";
    }

    @PostMapping("/admin/accounts/{id}/delete-to-archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public Object deleteAccountToArchive(@PathVariable("id") Long id,
                                         @RequestParam(value = "reason", required = false) String reason,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                         Principal principal,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {

        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Account not found"));
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Hesab tapılmadı.");
            return "redirect:/admin/security";
        }

        String adminName = principal != null ? principal.getName() : "Security";
        String archiveReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : "Təhlükəsizlik Paneli tərəfindən silindi və arxivə köçürüldü";

        String accNo = account.getAccountNumber();
        User user = account.getUser();
        String username = user != null ? user.getUsername() : "N/A";

        pendingAccountCleanupService.archiveAndPurgeAccount(account, null, adminName, archiveReason);

        SecurityNotification notif = new SecurityNotification(
                "Hesab Silindi və Arxivləşdirildi",
                String.format("🗄️ Hesab silinərək RavanArxiv bölməsinə köçürüldü: %s (@%s). İstifadəçi adı və şifrə yenidən istifadəyə açıldı.",
                        accNo, username),
                "ACCOUNT_DELETED",
                username
        );
        securityNotificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception ignored) {}

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", id,
                    "message", "Hesab silindi, RavanArxiv bölməsinə arxivləşdirildi və username/şifrə yenidən istifadəyə açıldı."
            ));
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Hesab uğurla silindi, 'RavanArxiv' bölməsinə köçürüldü və onun istifadəçi adı / şifrəsi yenidən istifadə üçün azad edildi.");
        return "redirect:/admin/security#accountsSection";
    }

    @GetMapping("/admin/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String showArchivePage(HttpSession session, Model model, Principal principal) {
        pendingAccountCleanupService.processPendingAccountsLifecycle();
        pendingAccountCleanupService.releasePreviouslyArchivedCredentials();

        Boolean isArchiveAuth = (Boolean) session.getAttribute("ARCHIVE_AUTHENTICATED");

        if (isArchiveAuth == null || !isArchiveAuth) {
            model.addAttribute("needsArchiveAuth", true);
            model.addAttribute("adminUsername", principal != null ? principal.getName() : "Ravan Asadov");
            return "archive";
        }

        List<ArchivedAccount> archivedAccounts = archivedAccountRepository.findAllByOrderByArchivedAtDesc();
        model.addAttribute("needsArchiveAuth", false);
        model.addAttribute("archivedAccounts", archivedAccounts);
        model.addAttribute("totalArchivedCount", archivedAccounts.size());
        model.addAttribute("adminUsername", principal != null ? principal.getName() : "Ravan Asadov");

        return "archive";
    }

    @PostMapping("/admin/archive/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String verifyArchiveCode(@RequestParam("archiveCode") String archiveCode,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {

        if ("RavanArxiv".equals(archiveCode != null ? archiveCode.trim() : "")) {
            session.setAttribute("ARCHIVE_AUTHENTICATED", true);
            redirectAttributes.addFlashAttribute("successMessage",
                    "🗄️ 'RavanArxiv' Məxfi Girişi Təsdiqləndi! Arxivləşdirilmiş hesablar bazasına xoş gəlmisiniz.");
        } else {
            session.removeAttribute("ARCHIVE_AUTHENTICATED");
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Daxil edilmiş 'RavanArxiv' məxfi kodu yanlışdır! Giriş qadağandır.");
        }

        return "redirect:/admin/archive";
    }

    @PostMapping("/admin/archive/logout")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String logoutArchive(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute("ARCHIVE_AUTHENTICATED");
        redirectAttributes.addFlashAttribute("successMessage", "RavanArxiv bölməsindən çıxış edildi.");
        return "redirect:/admin/security";
    }

    @PostMapping("/admin/security/messages/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String sendCustomerMessage(
            @RequestParam("recipientUsername") String recipientUsername,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "messageType", defaultValue = "INFO") String messageType,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            if (recipientUsername == null || recipientUsername.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Qəbuledici müştəri seçilməlidir.");
                return "redirect:/admin/security#messageSection";
            }
            if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Mesaj başlığı və mətni boş ola bilməz.");
                return "redirect:/admin/security#messageSection";
            }

            String sender = principal != null ? "AsadovBank Təhlükəsizlik Mərkəzi (" + principal.getName() + ")" : "AsadovBank Təhlükəsizlik Xidməti";

            final String rawRecipient = recipientUsername.trim();
            String cleanRecipient = rawRecipient;
            if (!"ALL".equalsIgnoreCase(rawRecipient)) {
                cleanRecipient = resolveCanonicalUsername(rawRecipient, null);
            }

            CustomerMessage msg = new CustomerMessage(
                    cleanRecipient,
                    sender,
                    title.trim(),
                    content.trim(),
                    messageType.trim()
            );
            customerMessageRepository.save(msg);

            SecurityNotification notif = new SecurityNotification(
                    "Müştəriyə Mesaj Göndərildi",
                    String.format("✉️ Müştəriyə (%s) rəsmi mesaj göndərildi: '%s'", cleanRecipient, title.trim()),
                    "MESSAGE_SENT",
                    cleanRecipient
            );
            securityNotificationRepository.save(notif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Mesaj uğurla göndərildi! Müştəri öz panelində bu mesajı dərhal görəcəkdir.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mesaj göndərilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin/security#messageSection";
    }

    private String resolveCanonicalUsername(String username, String customerName) {
        if (username != null && !username.trim().isEmpty()) {
            String u = username.trim();
            Optional<User> byUser = userRepository.findFirstByUsernameIgnoreCase(u);
            if (byUser.isPresent()) {
                return byUser.get().getUsername();
            }
            Optional<User> byEmail = userRepository.findFirstByEmailIgnoreCase(u);
            if (byEmail.isPresent()) {
                return byEmail.get().getUsername();
            }
            Optional<User> byName = userRepository.findFirstByFullNameIgnoreCase(u);
            if (byName.isPresent()) {
                return byName.get().getUsername();
            }
        }
        if (customerName != null && !customerName.trim().isEmpty()) {
            String c = customerName.trim();
            Optional<User> byName = userRepository.findFirstByFullNameIgnoreCase(c);
            if (byName.isPresent()) {
                return byName.get().getUsername();
            }
        }
        return username != null ? username.trim() : "";
    }

    @GetMapping("/api/security/notifications")
    @ResponseBody
    public ResponseEntity<List<SecurityNotification>> getLatestNotifications() {
        return ResponseEntity.ok(securityNotificationRepository.findTop50ByOrderByCreatedAtDesc());
    }

    @PostMapping("/admin/security/employees/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String approveEmployeeInSecurity(
            @PathVariable("id") Long id,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(session.getAttribute("SECURITY_AUTHENTICATED"))) {
            return "redirect:/admin/security";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            if (!"SENT_TO_SECURITY".equalsIgnoreCase(emp.getApprovalStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Bu işçi əvvəlcə Sorğu Paneli (Operator) tərəfindən Security-yə göndərilməlidir!");
                return "redirect:/admin/security";
            }
            String targetDept = departmentQuotaService.normalizeDepartmentName(emp.getDepartment());
            String quotaError = departmentQuotaService.checkQuotaError(targetDept, null);
            if (quotaError != null) {
                redirectAttributes.addFlashAttribute("errorMessage", quotaError);
                return "redirect:/admin/security";
            }

            String actor = principal != null ? principal.getName() : "Security";
            emp.setDepartment(targetDept);
            emp.setApprovalStatus("APPROVED");
            emp.setAccountStatus("ACTIVE");
            emp.setApprovedBy(actor);
            emp.setApprovedAt(LocalDateTime.now());
            emp.setEscalatedToAdmin(false);
            emp.setRole("Security".equalsIgnoreCase(targetDept) ? "ROLE_SECURITY" : "ROLE_EMPLOYEE");
            bankEmployeeRepository.save(emp);

            final String assignedRole = emp.getRole();
            userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).ifPresent(u -> {
                u.setRole(assignedRole);
                userRepository.save(u);
                accountRepository.findFirstByUser(u).ifPresent(acc -> {
                    acc.setStatus(AccountStatus.ACTIVE);
                    accountRepository.save(acc);
                    emp.setAccountNumber(acc.getAccountNumber());
                    emp.setBalance(acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO);
                    bankEmployeeRepository.save(emp);
                });
            });

            customerInquiryRepository.findAll().stream()
                    .filter(inq -> emp.getUsername().equalsIgnoreCase(inq.getUsername())
                            && inq.getReferenceNo() != null && inq.getReferenceNo().startsWith("JOB-"))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Security şöbəsi tərəfindən təsdiqləndi və işə qəbul olundu.");
                        inq.setRepliedBy(actor);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });

            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " (" + emp.getPosition() + ") Security tərəfindən TƏSDİQLƏNDİ və işə qəbul edildi!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "İşçi təsdiqlənmə xətası: " + e.getMessage());
        }
        return "redirect:/admin/security";
    }

    @PostMapping("/admin/security/employees/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String rejectEmployeeInSecurity(
            @PathVariable("id") Long id,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(session.getAttribute("SECURITY_AUTHENTICATED"))) {
            return "redirect:/admin/security";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            String actor = principal != null ? principal.getName() : "Security";
            emp.setApprovalStatus("REJECTED");
            emp.setAccountStatus("BLOCKED");
            emp.setEscalatedToAdmin(false);
            bankEmployeeRepository.save(emp);

            userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).ifPresent(u -> {
                u.setRole("ROLE_REJECTED");
                userRepository.save(u);
            });
            customerInquiryRepository.findAll().stream()
                    .filter(inq -> emp.getUsername().equalsIgnoreCase(inq.getUsername())
                            && inq.getReferenceNo() != null && inq.getReferenceNo().startsWith("JOB-"))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Security yoxlamasından keçmədi / rədd edildi.");
                        inq.setRepliedBy(actor);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });
            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " Security tərəfindən RƏDD EDİLDİ.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "İmtina xətası: " + e.getMessage());
        }
        return "redirect:/admin/security";
    }

    @PostMapping("/admin/security/employees/{id}/escalate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'SECURITY', 'ROLE_SECURITY', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String escalateEmployeeFromSecurityToAdmin(
            @PathVariable("id") Long id,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(session.getAttribute("SECURITY_AUTHENTICATED"))) {
            return "redirect:/admin/security";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            String actor = principal != null ? principal.getName() : "Security";
            String note = (reason != null && !reason.trim().isEmpty())
                    ? reason.trim() : "Security yoxlaması zamanı problem aşkarlandı / Admin əmri gözlənilir.";
            if (!"APPROVED".equalsIgnoreCase(emp.getApprovalStatus())) {
                emp.setApprovalStatus("SENT_TO_ADMIN");
            }
            emp.setEscalatedToAdmin(true);
            emp.setEscalationNote(note);
            emp.setEscalatedBy(actor + " (Security Paneli)");
            emp.setEscalatedAt(LocalDateTime.now());
            bankEmployeeRepository.save(emp);

            panelMessageRepository.save(new PanelMessage(
                    "SECURITY", "ADMIN", actor,
                    "⚠️ Problem / İşçi Yönləndirməsi: " + emp.getFullName() + " (" + emp.getEmployeeId() + ")",
                    String.format("Vəzifə: %s | İstifadəçi: @%s\nProblem / Qeyd: %s", emp.getPosition(), emp.getUsername(), note),
                    emp.getId()
            ));
            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " problem / yekun əmr üçün Admin Panelə yönləndirildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Adminə yönləndirmə xətası: " + e.getMessage());
        }
        return "redirect:/admin/security";
    }
}
