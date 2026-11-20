package com.financeapp.controller;

import com.financeapp.model.BankEmployee;
import com.financeapp.model.SecurityAppeal;
import com.financeapp.model.SecurityNotification;
import com.financeapp.repository.BankEmployeeRepository;
import com.financeapp.repository.SecurityAppealRepository;
import com.financeapp.repository.SecurityNotificationRepository;
import com.financeapp.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class EmployeePortalController {

    private final BankEmployeeRepository bankEmployeeRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final SecurityAppealRepository securityAppealRepository;
    private final com.financeapp.repository.AccountRepository accountRepository;
    private final com.financeapp.repository.UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final com.financeapp.service.PanelLockoutService panelLockoutService;

    public EmployeePortalController(BankEmployeeRepository bankEmployeeRepository,
                                    SecurityNotificationRepository securityNotificationRepository,
                                    SecurityAppealRepository securityAppealRepository,
                                    com.financeapp.repository.AccountRepository accountRepository,
                                    com.financeapp.repository.UserRepository userRepository,
                                    AuditLogService auditLogService,
                                    com.financeapp.service.PanelLockoutService panelLockoutService) {
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.securityAppealRepository = securityAppealRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.panelLockoutService = panelLockoutService;
    }

    @GetMapping("/employee/portal")
    @PreAuthorize("isAuthenticated()")
    public String showEmployeePortal(Principal principal, HttpSession session, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        String username = principal.getName();
        Optional<BankEmployee> empOpt = bankEmployeeRepository.findByUsername(username);

        if (empOpt.isEmpty()) {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
            if (isAdmin) {
                return "redirect:/admin";
            }
            model.addAttribute("employee", null);
            model.addAttribute("statusMessage", "No registered employee profile found for user: " + username);
            return "employee-portal";
        }

        BankEmployee employee = empOpt.get();
        userRepository.findFirstByUsernameIgnoreCase(employee.getUsername()).ifPresent(empUser -> {
            com.financeapp.model.Account empAcc = accountRepository.findFirstByUser(empUser).orElseGet(() -> {
                com.financeapp.model.Account acc = new com.financeapp.model.Account();
                acc.setAccountNumber(employee.getAccountNumber() != null && !employee.getAccountNumber().isEmpty()
                        ? employee.getAccountNumber()
                        : com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
                acc.setBalance(employee.getBalance() != null ? employee.getBalance() : java.math.BigDecimal.ZERO);
                acc.setCurrency("EUR");
                acc.setAccountType("SALARY");
                acc.setStatus("ACTIVE".equalsIgnoreCase(employee.getAccountStatus())
                        ? com.financeapp.enums.AccountStatus.ACTIVE
                        : com.financeapp.enums.AccountStatus.PENDING_VERIFICATION);
                acc.setUser(empUser);
                return accountRepository.save(acc);
            });
            employee.setAccountNumber(empAcc.getAccountNumber());
            employee.setBalance(empAcc.getBalance() != null ? empAcc.getBalance() : java.math.BigDecimal.ZERO);
            bankEmployeeRepository.save(employee);
            model.addAttribute("employeeAccount", empAcc);
        });
        if (employee.getAccountNumber() == null || employee.getAccountNumber().isEmpty()) {
            employee.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
            bankEmployeeRepository.save(employee);
        }
        model.addAttribute("employee", employee);

        if ("BLOCKED".equalsIgnoreCase(employee.getAccountStatus())) {
            model.addAttribute("isBlocked", true);
            model.addAttribute("statusMessage", "Your employee account has been BLOCKED by Administrator. Access to department workstations is disabled.");
            return "employee-portal";
        }

        if (!"APPROVED".equalsIgnoreCase(employee.getApprovalStatus())) {
            model.addAttribute("isPending", true);
            model.addAttribute("statusMessage", "Your employee registration is PENDING Administrator approval. Please await Admin review and activation.");
            return "employee-portal";
        }

        model.addAttribute("isApproved", true);
        model.addAttribute("isBlocked", false);
        model.addAttribute("department", employee.getDepartment());

        String stationDept = resolveStationDepartment(employee);
        boolean hasWorkstationPanel = !"NONE".equals(stationDept);
        model.addAttribute("normalizedDept", stationDept);
        model.addAttribute("hasWorkstationPanel", hasWorkstationPanel);

        boolean stationVerified = false;
        if ("Security".equals(stationDept)) {
            stationVerified = Boolean.TRUE.equals(session.getAttribute("SECURITY_AUTHENTICATED"));
        } else if ("Inquiry".equals(stationDept)) {
            stationVerified = Boolean.TRUE.equals(session.getAttribute("INQUIRY_AUTHENTICATED"));
        } else if ("Treasury".equals(stationDept)) {
            stationVerified = Boolean.TRUE.equals(session.getAttribute("TREASURY_AUTHENTICATED"));
        }
        model.addAttribute("stationVerified", stationVerified);

        return "employee-portal";
    }

    private String resolveStationDepartment(BankEmployee employee) {
        if (employee == null) {
            return "NONE";
        }
        String deptLower = employee.getDepartment() != null ? employee.getDepartment().trim().toLowerCase() : "";
        String posLower = employee.getPosition() != null ? employee.getPosition().trim().toLowerCase() : "";

        if (deptLower.contains("security") || deptLower.contains("təhlükə")
                || posLower.contains("security") || posLower.contains("təhlükə")) {
            return "Security";
        }
        if (deptLower.equals("inquiry") || deptLower.contains("sorğu") || deptLower.contains("sorgu")
                || deptLower.equals("operator") || posLower.equals("operator") || posLower.contains("sorğu")) {
            return "Inquiry";
        }
        if (deptLower.contains("treasury") || deptLower.contains("xəzinə") || deptLower.contains("xaznadar")
                || posLower.contains("treasury") || posLower.contains("xəzinə") || posLower.contains("xaznadar")) {
            return "Treasury";
        }
        return "NONE";
    }

    @PostMapping("/employee/portal/verify-code")
    @PreAuthorize("isAuthenticated()")
    public String verifyDepartmentCode(
            @RequestParam("departmentCode") String departmentCode,
            Principal principal,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        String username = principal.getName();
        Optional<BankEmployee> empOpt = bankEmployeeRepository.findByUsername(username);

        if (empOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Employee record not found for: " + username);
            return "redirect:/employee/portal";
        }

        BankEmployee employee = empOpt.get();
        String code = departmentCode != null ? departmentCode.trim() : "";
        String stationDept = resolveStationDepartment(employee);

        if ("NONE".equals(stationDept)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Bu vəzifə üçün iş stansiyası girişi nəzərdə tutulmayıb. Yalnız Security, Sorğu və Xəzinədar əməkdaşları şöbə stansiyasına keçid edə bilər.");
            return "redirect:/employee/portal";
        }

        if (!"Security".equalsIgnoreCase(stationDept) && "RavanBank".equalsIgnoreCase(code)) {
            String alertTitle = "🚨 CRITICAL: Unauthorized Security Access Code Attempt";
            String alertMsg = String.format("Staff member '%s' (%s, ID: %s) entered the Security Department Code ('RavanBank') from IP %s! Access was denied immediately.",
                    employee.getUsername(), employee.getFullName(), employee.getEmployeeId(), request.getRemoteAddr());

            SecurityNotification notification = new SecurityNotification(
                    alertTitle, alertMsg, "UNAUTHORIZED_SECURITY_CODE", employee.getUsername());
            securityNotificationRepository.save(notification);

            SecurityAppeal appeal = new SecurityAppeal(
                    employee.getUsername(),
                    employee.getFullName(),
                    "EMP-" + employee.getEmployeeId(),
                    employee.getPhoneNumber() != null ? employee.getPhoneNumber() : "",
                    "SECURITY_ALERT",
                    "🚨 Unauthorized Access Attempt: Security Code Used by Non-Security Staff",
                    alertMsg
            );
            appeal.setStatus("NEW");
            securityAppealRepository.save(appeal);

            auditLogService.logAction(username, "SECURITY_BREACH_ATTEMPT", "SECURITY",
                    employee.getEmployeeId(), "Non-security staff member attempted to use Security Code: 'RavanBank'. Blocked.");

            redirectAttributes.addFlashAttribute("errorMessage",
                    "🚨 ACCESS DENIED: You are NOT authorized to enter the Security Department Access Code! This unauthorized attempt has been logged and dispatched to Security Administration.");
            return "redirect:/employee/portal";
        }

        if ("Security".equals(stationDept)) {
            LocalDateTime lockedUntil = panelLockoutService.getActiveLockUntil("SECURITY");
            if (lockedUntil != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "❌ Çoxsaylı yanlış cəhd səbəbilə Security Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                                + lockedUntil.toString().substring(11, 16));
                return "redirect:/employee/portal";
            }
            if ("RavanBank".equalsIgnoreCase(code)) {
                panelLockoutService.resetFailures("SECURITY");
                session.setAttribute("SECURITY_AUTHENTICATED", true);
                session.setAttribute("EMPLOYEE_STATION_VERIFIED", true);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Security Station access verified! Welcome to Security / Təhlükəsizlik Center.");
                return "redirect:/admin/security";
            } else {
                int attempts = panelLockoutService.recordFailedAttempt("SECURITY");
                if (attempts >= com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS) {
                    LocalDateTime until = panelLockoutService.getActiveLockUntil("SECURITY");
                    String untilStr = until != null ? until.toString().substring(11, 16) : "";
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ 5 dəfə yanlış kod daxil edildiyi üçün Security Paneli 15 dəqiqəlik kilidə alındı. Zəhmət olmasa gözləyin: " + untilStr);
                } else {
                    int remaining = com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS - attempts;
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Yanlış Təhlükəsizlik Kodu! Giriş qadağandır (Qalan cəhd sayı: " + remaining + ").");
                }
                return "redirect:/employee/portal";
            }
        }

        if ("Inquiry".equals(stationDept)) {
            LocalDateTime lockedUntil = panelLockoutService.getActiveLockUntil("INQUIRY");
            if (lockedUntil != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "❌ Çoxsaylı yanlış cəhd səbəbilə Sorğu Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                                + lockedUntil.toString().substring(11, 16));
                return "redirect:/employee/portal";
            }
            if ("RavanInquiry".equalsIgnoreCase(code) || "RavanSorgu".equalsIgnoreCase(code) || "Inquiry2026".equalsIgnoreCase(code)) {
                panelLockoutService.resetFailures("INQUIRY");
                session.setAttribute("INQUIRY_AUTHENTICATED", true);
                session.setAttribute("EMPLOYEE_STATION_VERIFIED", true);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Inquiry Station access verified! Welcome to Inquiry / Sorğu Portal.");
                return "redirect:/admin/inquiry";
            } else {
                int attempts = panelLockoutService.recordFailedAttempt("INQUIRY");
                if (attempts >= com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS) {
                    LocalDateTime until = panelLockoutService.getActiveLockUntil("INQUIRY");
                    String untilStr = until != null ? until.toString().substring(11, 16) : "";
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ 5 dəfə yanlış kod daxil edildiyi üçün Sorğu Paneli 15 dəqiqəlik kilidə alındı. Zəhmət olmasa gözləyin: " + untilStr);
                } else {
                    int remaining = com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS - attempts;
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Yanlış Sorğu Şöbəsi Giriş Kodu! (Qalan cəhd sayı: " + remaining + ").");
                }
                return "redirect:/employee/portal";
            }
        }

        if ("Treasury".equals(stationDept)) {
            LocalDateTime lockedUntil = panelLockoutService.getActiveLockUntil("TREASURY");
            if (lockedUntil != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "❌ Çoxsaylı yanlış cəhd səbəbilə Xəzinədar Paneli təhlükəsizlik kilidinə alınıb. Zəhmət olmasa gözləyin: "
                                + lockedUntil.toString().substring(11, 16));
                return "redirect:/employee/portal";
            }
            if ("RavanXaznadar".equalsIgnoreCase(code)) {
                panelLockoutService.resetFailures("TREASURY");
                session.setAttribute("TREASURY_AUTHENTICATED", true);
                session.setAttribute("EMPLOYEE_STATION_VERIFIED", true);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Xəzinədar / Treasury Panelinə giriş təsdiqləndi!");
                return "redirect:/admin/treasury";
            } else {
                int attempts = panelLockoutService.recordFailedAttempt("TREASURY");
                if (attempts >= com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS) {
                    LocalDateTime until = panelLockoutService.getActiveLockUntil("TREASURY");
                    String untilStr = until != null ? until.toString().substring(11, 16) : "";
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ 5 dəfə yanlış kod daxil edildiyi üçün Xəzinədar Paneli 15 dəqiqəlik kilidə alındı. Zəhmət olmasa gözləyin: " + untilStr);
                } else {
                    int remaining = com.financeapp.service.PanelLockoutService.MAX_ATTEMPTS - attempts;
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Yanlış Xəzinədar şöbə giriş kodu! (Qalan cəhd sayı: " + remaining + ").");
                }
                return "redirect:/employee/portal";
            }
        }

        redirectAttributes.addFlashAttribute("errorMessage", "Unknown department assignment.");
        return "redirect:/employee/portal";
    }
}
