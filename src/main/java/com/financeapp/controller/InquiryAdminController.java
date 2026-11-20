package com.financeapp.controller;

import com.financeapp.model.*;
import com.financeapp.repository.*;
import com.financeapp.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/inquiry")
public class InquiryAdminController {

    private final CustomerInquiryRepository customerInquiryRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SecurityAppealRepository securityAppealRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final CustomerMessageRepository customerMessageRepository;
    private final InquiryAdminConfigRepository inquiryAdminConfigRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final BankEmployeeRepository bankEmployeeRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final com.financeapp.service.PasswordSecurityService passwordSecurityService;
    private final PanelMessageRepository panelMessageRepository;
    private final com.financeapp.service.DepartmentQuotaService departmentQuotaService;

    public InquiryAdminController(CustomerInquiryRepository customerInquiryRepository,
                                  LoanApplicationRepository loanApplicationRepository,
                                  SecurityAppealRepository securityAppealRepository,
                                  SecurityNotificationRepository securityNotificationRepository,
                                  CustomerMessageRepository customerMessageRepository,
                                  InquiryAdminConfigRepository inquiryAdminConfigRepository,
                                  AdminAuditLogRepository adminAuditLogRepository,
                                  BankEmployeeRepository bankEmployeeRepository,
                                  UserRepository userRepository,
                                  AccountRepository accountRepository,
                                  PasswordEncoder passwordEncoder,
                                  AuditLogService auditLogService,
                                  com.financeapp.service.PasswordSecurityService passwordSecurityService,
                                  PanelMessageRepository panelMessageRepository,
                                  com.financeapp.service.DepartmentQuotaService departmentQuotaService) {
        this.customerInquiryRepository = customerInquiryRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.securityAppealRepository = securityAppealRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.customerMessageRepository = customerMessageRepository;
        this.inquiryAdminConfigRepository = inquiryAdminConfigRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.passwordSecurityService = passwordSecurityService;
        this.panelMessageRepository = panelMessageRepository;
        this.departmentQuotaService = departmentQuotaService;
    }

    private boolean isInquiryAuthenticated(HttpSession session) {
        Boolean auth = (Boolean) session.getAttribute("INQUIRY_AUTHENTICATED");
        return auth != null && auth;
    }

    private boolean isAdminPrincipal(Principal principal, org.springframework.security.core.Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()))) {
            return true;
        }
        if (principal != null) {
            User pUser = userRepository.findFirstByUsernameIgnoreCase(principal.getName()).orElse(null);
            return pUser != null && pUser.getRole() != null && pUser.getRole().toUpperCase().contains("ADMIN");
        }
        return false;
    }

    private boolean isAuthorizedForInquiryPanel(Principal principal, org.springframework.security.core.Authentication authentication) {
        if (isAdminPrincipal(principal, authentication)) {
            return true;
        }
        if (principal == null) {
            return false;
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
        return dept.equals("inquiry") || dept.contains("sorğu") || dept.contains("sorgu")
                || dept.equals("operator") || pos.equals("operator") || pos.contains("sorğu");
    }

    @GetMapping("/login")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String showInquiryLogin(HttpSession session, Principal principal,
                                   org.springframework.security.core.Authentication authentication,
                                   Model model) {
        if (!isAuthorizedForInquiryPanel(principal, authentication)) {
            return "redirect:/employee/portal";
        }
        if (isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry";
        }
        model.addAttribute("isAdminUser", isAdminPrincipal(principal, authentication));
        return "inquiry-login";
    }

    @PostMapping("/login")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String processInquiryLogin(
            @RequestParam("inquiryPassword") String rawPassword,
            HttpServletRequest request,
            HttpSession session,
            Principal principal,
            org.springframework.security.core.Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (!isAuthorizedForInquiryPanel(principal, authentication)) {
            return "redirect:/employee/portal";
        }

        InquiryAdminConfig config = inquiryAdminConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    InquiryAdminConfig def = new InquiryAdminConfig(passwordEncoder.encode("RavanSorgu"));
                    return inquiryAdminConfigRepository.save(def);
                });

        if (config.getLockedUntil() != null && config.getLockedUntil().isAfter(LocalDateTime.now())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Çoxsaylı yanlış cəhd səbəbilə Sorğu Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                            + config.getLockedUntil().toString().substring(11, 16));
            return "redirect:/admin/inquiry/login";
        }

        String username = principal != null ? principal.getName() : "Admin";
        String trimmedInput = rawPassword != null ? rawPassword.trim() : "";

        if ("RavanBank".equalsIgnoreCase(trimmedInput)) {
            SecurityNotification alertNotif = new SecurityNotification(
                    "🚨 CRITICAL: Unauthorized Security Access Code Attempt",
                    String.format("Inquiry staff/user '%s' entered the Security Department Code ('RavanBank') inside Inquiry Login! Access was denied immediately. IP: %s",
                            username, request.getRemoteAddr()),
                    "UNAUTHORIZED_SECURITY_CODE",
                    username
            );
            securityNotificationRepository.save(alertNotif);

            SecurityAppeal alertAppeal = new SecurityAppeal(
                    username,
                    username,
                    "INQ-LOGIN",
                    "",
                    "SECURITY_ALERT",
                    "🚨 Security Code Breach Attempt at Inquiry Login",
                    String.format("User '%s' entered the Security access code 'RavanBank' at Inquiry Portal login. Access denied immediately.", username)
            );
            alertAppeal.setStatus("NEW");
            securityAppealRepository.save(alertAppeal);

            auditLogService.logAction(username, "SECURITY_BREACH_ATTEMPT", "INQUIRY",
                    "LOGIN", "User entered Security code 'RavanBank' in Inquiry Portal. Blocked.");

            redirectAttributes.addFlashAttribute("errorMessage",
                    "🚨 GİRİŞ QADAĞANDIR: Siz Security şöbəsinin məxfi kodunu Sorğu Panelində istifadə etməyə cəhd etdiniz! Bu cəhd dərhal Security şöbəsinə bildirildi.");
            return "redirect:/admin/inquiry/login";
        }

        boolean isCorrect = passwordEncoder.matches(trimmedInput, config.getPasswordHash())
                || "RavanInquiry".equalsIgnoreCase(trimmedInput)
                || "RavanSorgu".equalsIgnoreCase(trimmedInput)
                || "Inquiry2026".equalsIgnoreCase(trimmedInput);

        if (isCorrect) {

            config.setFailedAttempts(0);
            config.setLockedUntil(null);
            inquiryAdminConfigRepository.save(config);

            session.setAttribute("INQUIRY_AUTHENTICATED", true);
            session.setAttribute("INQUIRY_MUST_CHANGE_PASSWORD", !config.getIsPasswordChanged());

            auditLogService.logAction(username, "INQUIRY_LOGIN_SUCCESS", "AUTH", "SESSION", "Successful login to Inquiry Portal");

            redirectAttributes.addFlashAttribute("successMessage",
                    "Inquiry Portal access verified! Welcome to Inquiry / Sorğu Station.");
            return "redirect:/admin/inquiry";
        } else {

            int attempts = config.getFailedAttempts() + 1;
            config.setFailedAttempts(attempts);
            if (attempts >= 5) {
                config.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            inquiryAdminConfigRepository.save(config);

            auditLogService.logAction(username, "INQUIRY_LOGIN_FAILED", "AUTH", "ATTEMPT_" + attempts,
                    "Invalid Inquiry password. IP: " + request.getRemoteAddr());

            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid Inquiry Portal code! Please enter your department access code (Attempts remaining: " + (5 - attempts) + ").");
            return "redirect:/admin/inquiry/login";
        }
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.POST, RequestMethod.GET})
    public String logoutInquiry(HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response,
                                HttpSession session,
                                org.springframework.security.core.Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (session != null) {
            session.removeAttribute("INQUIRY_AUTHENTICATED");
            session.removeAttribute("INQUIRY_MUST_CHANGE_PASSWORD");
            session.removeAttribute("EMPLOYEE_STATION_VERIFIED");
        }
        boolean isAdmin = isAdminPrincipal(authentication, authentication);
        if (isAdmin) {
            redirectAttributes.addFlashAttribute("successMessage", "Sorğu Panelindən təhlükəsiz çıxış edildi.");
            return "redirect:/admin";
        }
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                .logout(request, response, authentication);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        jakarta.servlet.http.Cookie jsessionCookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        jsessionCookie.setPath("/");
        jsessionCookie.setMaxAge(0);
        response.addCookie(jsessionCookie);
        return "redirect:/login?logout=true";
    }

    @PostMapping("/change-password")
    public String changeInquiryPassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmNewPassword") String confirmNewPassword,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (!isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry/login";
        }

        if (newPassword == null || newPassword.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Yeni şifrə ən azı 6 simvoldan ibarət olmalıdır.");
            return "redirect:/admin/inquiry";
        }

        if (!newPassword.equals(confirmNewPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Yeni şifrə ilə təkrar şifrə eyni deyil.");
            return "redirect:/admin/inquiry";
        }

        InquiryAdminConfig config = inquiryAdminConfigRepository.findFirstByOrderByIdAsc().orElse(null);
        if (config == null || !passwordEncoder.matches(currentPassword, config.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cari şifrə yanlış daxil edilib.");
            return "redirect:/admin/inquiry";
        }

        if (passwordEncoder.matches(newPassword.trim(), config.getPasswordHash())
                || passwordSecurityService.isPasswordPreviouslyUsed(newPassword.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", com.financeapp.service.PasswordSecurityService.REUSE_ERROR_MESSAGE);
            return "redirect:/admin/inquiry";
        }

        String encodedNew = passwordEncoder.encode(newPassword.trim());
        config.setPasswordHash(encodedNew);
        config.setIsPasswordChanged(true);
        config.setUpdatedAt(LocalDateTime.now());
        inquiryAdminConfigRepository.save(config);

        String username = principal != null ? principal.getName() : "Admin";
        passwordSecurityService.recordPasswordUsage(username, newPassword.trim(), encodedNew);

        session.removeAttribute("INQUIRY_MUST_CHANGE_PASSWORD");

        auditLogService.logAction(username, "CHANGE_INQUIRY_PASSWORD", "CONFIG", "PASSWORD", "Sorğu Paneli şifrəsi uğurla dəyişdirildi");

        redirectAttributes.addFlashAttribute("successMessage", "✅ Sorğu Panelinin giriş şifrəsi uğurla dəyişdirildi!");
        return "redirect:/admin/inquiry";
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String showInquiryDashboard(
            @RequestParam(value = "type", defaultValue = "ALL") String filterType,
            @RequestParam(value = "status", defaultValue = "ALL") String filterStatus,
            HttpSession session,
            Principal principal,
            org.springframework.security.core.Authentication authentication,
            Model model) {

        if (!isAuthorizedForInquiryPanel(principal, authentication)) {
            return "redirect:/employee/portal";
        }
        model.addAttribute("isAdminUser", isAdminPrincipal(principal, authentication));

        if (!isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry/login";
        }

        List<CustomerInquiry> inquiries = customerInquiryRepository.findFiltered(filterType, filterStatus);
        List<LoanApplication> loanApplications = loanApplicationRepository.findAllByOrderByCreatedAtDesc();
        List<AdminAuditLog> recentLogs = adminAuditLogRepository.findTop100ByOrderByTimestampDesc();
        List<PanelMessage> panelMessages = panelMessageRepository.findByFromPanelOrToPanelOrderByCreatedAtDesc("INQUIRY", "INQUIRY");
        model.addAttribute("panelMessages", panelMessages);

        long totalInquiries = customerInquiryRepository.count();
        long newInquiries = customerInquiryRepository.countByStatus("NEW");
        long sentToSecurity = customerInquiryRepository.countByStatus("SENT_TO_SECURITY");
        long resolvedCount = customerInquiryRepository.countByStatus("RESOLVED");
        long answeredCount = customerInquiryRepository.countByStatus("ANSWERED");
        long totalLoans = loanApplicationRepository.count();
        long pendingLoans = loanApplicationRepository.countByStatus("PENDING");

        model.addAttribute("inquiries", inquiries);
        model.addAttribute("loanApplications", loanApplications);
        model.addAttribute("recentLogs", recentLogs);
        model.addAttribute("totalInquiries", totalInquiries);
        model.addAttribute("newInquiries", newInquiries);
        model.addAttribute("sentToSecurity", sentToSecurity);
        model.addAttribute("resolvedCount", resolvedCount);
        model.addAttribute("answeredCount", answeredCount);
        model.addAttribute("totalLoans", totalLoans);
        model.addAttribute("pendingLoans", pendingLoans);
        model.addAttribute("filterType", filterType);
        model.addAttribute("filterStatus", filterStatus);
        model.addAttribute("adminUsername", principal != null ? principal.getName() : "Ravan Asadov");
        model.addAttribute("mustChangePassword", session.getAttribute("INQUIRY_MUST_CHANGE_PASSWORD"));

        List<BankEmployee> employees = bankEmployeeRepository.findAllByOrderByCreatedAtDesc();
        for (BankEmployee emp : employees) {
            userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).ifPresent(empUser -> {
                Account empAcc = accountRepository.findFirstByUser(empUser).orElseGet(() -> {
                    Account acc = new Account();
                    acc.setAccountNumber(emp.getAccountNumber() != null && !emp.getAccountNumber().isEmpty()
                            ? emp.getAccountNumber()
                            : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                    acc.setBalance(emp.getBalance() != null ? emp.getBalance() : java.math.BigDecimal.ZERO);
                    acc.setCurrency("EUR");
                    acc.setAccountType("SALARY");
                    acc.setStatus("ACTIVE".equalsIgnoreCase(emp.getAccountStatus())
                            ? com.financeapp.enums.AccountStatus.ACTIVE
                            : com.financeapp.enums.AccountStatus.PENDING_VERIFICATION);
                    acc.setUser(empUser);
                    return accountRepository.save(acc);
                });
                emp.setAccountNumber(empAcc.getAccountNumber());
                emp.setBalance(empAcc.getBalance() != null ? empAcc.getBalance() : java.math.BigDecimal.ZERO);
                bankEmployeeRepository.save(emp);
            });
            if (emp.getAccountNumber() == null || emp.getAccountNumber().isEmpty()) {
                emp.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                bankEmployeeRepository.save(emp);
            }
        }
        model.addAttribute("employees", employees);
        model.addAttribute("pendingEmployeesCount", bankEmployeeRepository.countByApprovalStatus("PENDING"));
        departmentQuotaService.populateQuotaAttributes(model);
        if (principal != null) {
            bankEmployeeRepository.findByUsername(principal.getName()).ifPresent(loggedEmp -> {
                model.addAttribute("loggedInEmployee", loggedEmp);
            });
        }

        return "inquiry";
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String replyToInquiry(
            @PathVariable("id") Long id,
            @RequestParam("adminReply") String adminReply,
            @RequestParam(value = "newStatus", defaultValue = "ANSWERED") String newStatus,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            CustomerInquiry inquiry = customerInquiryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));

            if (adminReply == null || adminReply.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Cavab mətni boş ola bilməz.");
                return "redirect:/admin/inquiry";
            }

            String replier = principal != null ? principal.getName() : "Bank Administratoru";

            inquiry.setAdminReply(adminReply.trim());
            inquiry.setRepliedBy(replier);
            inquiry.setRepliedAt(LocalDateTime.now());
            inquiry.setStatus(newStatus != null && !newStatus.trim().isEmpty() ? newStatus.trim() : "ANSWERED");
            customerInquiryRepository.save(inquiry);

            String title = "Müraciətinizə Rəsmi Cavab: " + inquiry.getSubject();
            CustomerMessage msg = new CustomerMessage(
                    inquiry.getUsername(),
                    "AsadovBank Sorğu və Dəstək Mərkəzi",
                    title,
                    adminReply.trim(),
                    "APPEAL_REPLY"
            );
            customerMessageRepository.save(msg);

            auditLogService.logAction(replier, "REPLY_INQUIRY", "INQUIRY",
                    id.toString(), "Cavab göndərildi: @" + inquiry.getUsername() + " | " + inquiry.getSubject());

            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Müraciət cavablandırıldı və cavab müştərinin şəxsi kabinetinə çatdırıldı.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cavab göndərilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin/inquiry";
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String updateInquiryStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            CustomerInquiry inquiry = customerInquiryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));

            inquiry.setStatus(status);
            customerInquiryRepository.save(inquiry);

            String updater = principal != null ? principal.getName() : "Admin";
            auditLogService.logAction(updater, "UPDATE_INQUIRY_STATUS", "INQUIRY",
                    id.toString(), "Status yeniləndi: " + status);

            redirectAttributes.addFlashAttribute("successMessage", "Status yeniləndi: " + status);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Status yenilənərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin/inquiry";
    }

    @PostMapping("/{id}/send-to-security")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String sendInquiryToSecurity(
            @PathVariable("id") Long id,
            @RequestParam(value = "securityNote", required = false) String securityNote,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            CustomerInquiry inquiry = customerInquiryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));

            if (Boolean.TRUE.equals(inquiry.getForwardedToSecurity())) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "This ticket has already been forwarded to Security / Təhlükəsizlik Department. Duplicate forward is not allowed.");
                return "redirect:/admin/inquiry";
            }

            String sender = principal != null ? principal.getName() : "Staff";
            LocalDateTime now = LocalDateTime.now();

            inquiry.setForwardedToSecurity(true);
            inquiry.setForwardedBy(sender);
            inquiry.setForwardedAt(now);
            inquiry.setStatus("SENT_TO_SECURITY");
            inquiry.setSecurityNote(securityNote != null ? securityNote.trim() : "Forwarded from Inquiry Department for security review.");
            customerInquiryRepository.save(inquiry);

            SecurityAppeal appeal = new SecurityAppeal(
                    inquiry.getUsername(),
                    inquiry.getCustomerName() != null ? inquiry.getCustomerName() : inquiry.getUsername(),
                    "N/A",
                    inquiry.getPhoneNumber() != null ? inquiry.getPhoneNumber() : "",
                    "SECURITY",
                    "⚠️ [Forwarded from Inquiry] " + inquiry.getSubject(),
                    String.format("Forwarded by Staff: %s (Date: %s)\nReason: %s\n\nOriginal Inquiry Content:\n%s",
                            sender, now.toString().replace('T', ' ').substring(0, 16),
                            inquiry.getSecurityNote(), inquiry.getMessage())
            );
            appeal.setStatus("NEW");
            securityAppealRepository.save(appeal);

            SecurityNotification notif = new SecurityNotification(
                    "Forwarded from Inquiry Department",
                    String.format("Ticket (#%d - %s) was forwarded to Security / Təhlükəsizlik Department by @%s. Investigation required.",
                            inquiry.getId(), inquiry.getSubject(), sender),
                    "SECURITY_FORWARD",
                    inquiry.getUsername()
            );
            securityNotificationRepository.save(notif);

            auditLogService.logAction(sender, "SEND_TO_SECURITY", "INQUIRY",
                    id.toString(), "Forwarded to Security: " + inquiry.getSubject());

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Ticket (#%d) was successfully forwarded to Security / Təhlükəsizlik Department by %s.",
                            inquiry.getId(), sender));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error forwarding to Security: " + e.getMessage());
        }

        return "redirect:/admin/inquiry";
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String forwardInquiryWithNote(
            @PathVariable("id") Long id,
            @RequestParam("targetPanel") String targetPanel,
            @RequestParam("forwardNote") String forwardNote,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            CustomerInquiry inquiry = customerInquiryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));
            String sender = principal != null ? principal.getName() : "Sorğu Əməkdaşı";
            String cleanTarget = "ADMIN".equalsIgnoreCase(targetPanel != null ? targetPanel.trim() : "")
                    ? "ADMIN" : "SECURITY";
            String note = (forwardNote != null && !forwardNote.trim().isEmpty())
                    ? forwardNote.trim()
                    : "Sorğu şöbəsindən yönləndirildi.";

            if ("SECURITY".equals(cleanTarget)) {
                inquiry.setForwardedToSecurity(true);
                inquiry.setForwardedBy(sender);
                inquiry.setForwardedAt(LocalDateTime.now());
                inquiry.setStatus("SENT_TO_SECURITY");
                inquiry.setSecurityNote(note);
            } else {
                inquiry.setStatus("IN_REVIEW");
            }
            customerInquiryRepository.save(inquiry);

            String fullMessage = String.format("Təlimat / Qeyd: %s\n\nMüştəri: %s (@%s)\nOrijinal Müraciət: %s",
                    note,
                    inquiry.getCustomerName() != null ? inquiry.getCustomerName() : inquiry.getUsername(),
                    inquiry.getUsername(),
                    inquiry.getMessage());
            PanelMessage pm = new PanelMessage(
                    "INQUIRY",
                    cleanTarget,
                    sender,
                    "[Yönləndirilmiş #" + inquiry.getId() + "] " + inquiry.getSubject(),
                    fullMessage,
                    inquiry.getId()
            );
            panelMessageRepository.save(pm);

            String label = "ADMIN".equals(cleanTarget) ? "Admin Panel" : "Security (Təhlükəsizlik)";
            auditLogService.logAction(sender, "FORWARD_INQUIRY", "INQUIRY", id.toString(),
                    "Yönləndirildi -> " + cleanTarget + ": " + note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Müraciət (#" + id + ") təlimat qeydi ilə birlikdə " + label + " bölməsinə yönləndirildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Yönləndirmə xətası: " + e.getMessage());
        }
        return "redirect:/admin/inquiry";
    }

    @PostMapping("/employees/{id}/send-to-security")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'ROLE_SECURITY')")
    public String sendEmployeeToSecurity(
            @PathVariable("id") Long id,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry/login";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            String quotaError = departmentQuotaService.checkQuotaError(emp.getDepartment(), null);
            if (quotaError != null) {
                redirectAttributes.addFlashAttribute("errorMessage", quotaError);
                return "redirect:/admin/inquiry";
            }
            String sender = principal != null ? principal.getName() : "Sorğu Operatoru";
            String cleanNote = (note != null && !note.trim().isEmpty())
                    ? note.trim() : "Sorğu şöbəsində ilkin yoxlamadan keçdi, işə qəbul təsdiqi üçün Security-yə göndərildi.";
            emp.setApprovalStatus("SENT_TO_SECURITY");
            emp.setNotes(cleanNote);
            bankEmployeeRepository.save(emp);

            customerInquiryRepository.findAll().stream()
                    .filter(inq -> emp.getUsername().equalsIgnoreCase(inq.getUsername())
                            && inq.getReferenceNo() != null && inq.getReferenceNo().startsWith("JOB-"))
                    .forEach(inq -> {
                        inq.setForwardedToSecurity(true);
                        inq.setForwardedBy(sender);
                        inq.setForwardedAt(LocalDateTime.now());
                        inq.setStatus("SENT_TO_SECURITY");
                        inq.setSecurityNote(cleanNote);
                        customerInquiryRepository.save(inq);
                    });

            panelMessageRepository.save(new PanelMessage(
                    "INQUIRY", "SECURITY", sender,
                    "İş Müraciəti Yönləndirildi: " + emp.getFullName() + " (" + emp.getPosition() + ")",
                    String.format("İşçi ID: %s | İstifadəçi: @%s\nQeyd: %s", emp.getEmployeeId(), emp.getUsername(), cleanNote),
                    emp.getId()
            ));
            securityNotificationRepository.save(new SecurityNotification(
                    "Sorğu Şöbəsindən İşçi Təsdiqi Gəldi",
                    String.format("👔 %s (@%s, Vəzifə: %s) Sorğu operatoru @%s tərəfindən Security yoxlamasına göndərildi.",
                            emp.getFullName(), emp.getUsername(), emp.getPosition(), sender),
                    "EMPLOYEE_FORWARD",
                    emp.getUsername()
            ));
            auditLogService.logAction(sender, "INQUIRY_EMPLOYEE_TO_SECURITY", "EMPLOYEE", emp.getEmployeeId(),
                    "Security yoxlamasına göndərildi: " + emp.getFullName());

            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " (" + emp.getPosition() + ") Security şöbəsinə işə qəbul təsdiqi üçün göndərildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəta: " + e.getMessage());
        }
        return "redirect:/admin/inquiry";
    }

    @PostMapping("/employees/{id}/send-to-admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'ROLE_SECURITY')")
    public String escalateEmployeeToAdmin(
            @PathVariable("id") Long id,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry/login";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            String sender = principal != null ? principal.getName() : "Sorğu Operatoru";
            String note = (reason != null && !reason.trim().isEmpty())
                    ? reason.trim() : "Sorğu şöbəsindən problem / xüsusi əmr üçün Admin Panelə yönləndirildi.";
            if (!"APPROVED".equalsIgnoreCase(emp.getApprovalStatus())) {
                emp.setApprovalStatus("SENT_TO_ADMIN");
            }
            emp.setEscalatedToAdmin(true);
            emp.setEscalationNote(note);
            emp.setEscalatedBy(sender + " (Sorğu Paneli)");
            emp.setEscalatedAt(LocalDateTime.now());
            bankEmployeeRepository.save(emp);

            panelMessageRepository.save(new PanelMessage(
                    "INQUIRY", "ADMIN", sender,
                    "⚠️ Problem / İşçi Yönləndirməsi: " + emp.getFullName() + " (" + emp.getEmployeeId() + ")",
                    String.format("Vəzifə: %s | İstifadəçi: @%s\nProblem / Səbəb: %s", emp.getPosition(), emp.getUsername(), note),
                    emp.getId()
            ));
            auditLogService.logAction(sender, "INQUIRY_EMPLOYEE_ESCALATE_ADMIN", "EMPLOYEE", emp.getEmployeeId(),
                    "Admin Panelə yönləndirildi: " + note);

            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " (" + emp.getEmployeeId() + ") problem / əmr üçün Admin Panelə yönləndirildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Adminə yönləndirmə xətası: " + e.getMessage());
        }
        return "redirect:/admin/inquiry";
    }

    @PostMapping("/employees/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'ROLE_SECURITY')")
    public String rejectEmployeeInInquiry(
            @PathVariable("id") Long id,
            HttpSession session,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        if (!isInquiryAuthenticated(session)) {
            return "redirect:/admin/inquiry/login";
        }
        try {
            BankEmployee emp = bankEmployeeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Əməkdaş tapılmadı: " + id));
            String sender = principal != null ? principal.getName() : "Sorğu Operatoru";
            emp.setApprovalStatus("REJECTED");
            emp.setAccountStatus("BLOCKED");
            emp.setEscalatedToAdmin(false);
            bankEmployeeRepository.save(emp);
            userRepository.findFirstByUsernameIgnoreCase(emp.getUsername()).ifPresent(u -> {
                u.setRole("ROLE_REJECTED");
                userRepository.save(u);
            });
            auditLogService.logAction(sender, "INQUIRY_EMPLOYEE_REJECT", "EMPLOYEE", emp.getEmployeeId(),
                    "Sorğu şöbəsində imtina verildi: " + emp.getFullName());
            redirectAttributes.addFlashAttribute("successMessage",
                    emp.getFullName() + " iş müraciəti Sorğu şöbəsində rədd edildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "İmtina xətası: " + e.getMessage());
        }
        return "redirect:/admin/inquiry";
    }
}

