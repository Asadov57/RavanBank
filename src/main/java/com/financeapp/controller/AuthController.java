package com.financeapp.controller;

import com.financeapp.service.EmailService;
import com.financeapp.service.UserService;
import com.financeapp.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository;
    private final com.financeapp.repository.UserRepository userRepository;
    private final com.financeapp.repository.SecurityNotificationRepository securityNotificationRepository;
    private final com.financeapp.repository.CustomerInquiryRepository customerInquiryRepository;
    private final com.financeapp.repository.AccountRepository accountRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.financeapp.service.AuditLogService auditLogService;
    private final com.financeapp.service.PasswordSecurityService passwordSecurityService;
    private final com.financeapp.service.DepartmentQuotaService departmentQuotaService;
    private final com.financeapp.service.PendingAccountCleanupService pendingAccountCleanupService;

    public AuthController(UserService userService,
                          EmailService emailService,
                          com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository,
                          com.financeapp.repository.UserRepository userRepository,
                          com.financeapp.repository.SecurityNotificationRepository securityNotificationRepository,
                          com.financeapp.repository.CustomerInquiryRepository customerInquiryRepository,
                          com.financeapp.repository.AccountRepository accountRepository,
                          org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                          com.financeapp.service.AuditLogService auditLogService,
                          com.financeapp.service.PasswordSecurityService passwordSecurityService,
                          com.financeapp.service.DepartmentQuotaService departmentQuotaService,
                          com.financeapp.service.PendingAccountCleanupService pendingAccountCleanupService) {
        this.userService = userService;
        this.emailService = emailService;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.userRepository = userRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.customerInquiryRepository = customerInquiryRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.passwordSecurityService = passwordSecurityService;
        this.departmentQuotaService = departmentQuotaService;
        this.pendingAccountCleanupService = pendingAccountCleanupService;
    }

    @GetMapping("/")
    public String home() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isLoggedIn(auth)) {
            return isAdmin(auth) ? "redirect:/admin" : "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "registered", required = false) String registered,
            @RequestParam(value = "accessDenied", required = false) String accessDenied,
            Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isLoggedIn(auth)) {
            return isAdmin(auth) ? "redirect:/admin" : "redirect:/dashboard";
        }

        if (error != null) {
            model.addAttribute("error", true);
        }
        if (logout != null) {
            model.addAttribute("logout", true);
        }
        if (registered != null) {
            model.addAttribute("registered", true);
        }
        if (accessDenied != null) {
            model.addAttribute("accessDenied", true);
        }

        return "login";
    }

    @GetMapping("/register")
    public String showRegisterForm(HttpServletRequest request, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isLoggedIn(auth)) {
            return isAdmin(auth) ? "redirect:/admin" : "redirect:/dashboard";
        }
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
            model.addAttribute("_csrf", token);
        }
        departmentQuotaService.populateQuotaAttributes(model);
        return "register";
    }

    @PostMapping(value = {"/send-code", "/register/send-code", "/register/send-sms"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendVerificationCodeAjax(
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "accountType", required = false) String accountType,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam(value = "finCode", required = false) String finCode,
            @RequestParam(value = "idCardSerial", required = false) String idCardSerial,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword) {

        Map<String, Object> response = new HashMap<>();
        try {

            String targetEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : "";
            if (targetEmail.isEmpty()) {
                response.put("success", false);
                response.put("message", "Gmail ünvanı mütləq daxil edilməlidir.");
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }
            UserServiceImpl.validateEmail(targetEmail);

            if ((username == null || username.trim().isEmpty()) && (password == null || password.trim().isEmpty())) {
                String code = emailService.prepareOtpCode(targetEmail);
                emailService.sendOtpCode(targetEmail, "Müştəri", code);
                response.put("success", true);
                response.put("email", targetEmail);
                response.put("message", "3 rəqəmli təsdiq kodu e-poçt ünvanınıza göndərildi: " + targetEmail);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            UserServiceImpl.validateFullName(fullName);

            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "İstifadəçi adı boş ola bilməz.");
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            if (isUsernameTaken(username)) {
                response.put("success", false);
                response.put("message", UserServiceImpl.USERNAME_TAKEN_MESSAGE);
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            UserServiceImpl.validatePassword(password);

            if (confirmPassword != null && !password.trim().equals(confirmPassword.trim())) {
                response.put("success", false);
                response.put("message", "Şifrələr uyğun gəlmir! Hər iki sahəyə eyni şifrəni daxil edin.");
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            if (passwordSecurityService.isPasswordPreviouslyUsed(password.trim(), username)) {
                response.put("success", false);
                response.put("message", com.financeapp.service.PasswordSecurityService.REUSE_ERROR_MESSAGE);
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            boolean isCreditType = "CREDIT".equalsIgnoreCase(accountType != null ? accountType.trim() : "");
            if (isCreditType) {
                if (idCardSerial == null || idCardSerial.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Kredit müraciəti üçün şəxsiyyət vəsiqəsinin seriya nömrəsi mütləqdir.");
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                }
                if (finCode == null || finCode.trim().isEmpty()) {
                    response.put("success", false);
                    response.put("message", "Kredit müraciəti üçün FIN kod mütləqdir.");
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                }
            }

            if (idCardSerial != null && !idCardSerial.trim().isEmpty()) {
                String cleanSerial = idCardSerial.trim().toUpperCase().replaceAll("\\s+", "");
                if (cleanSerial.matches("^(\\w)\\1+$")) {
                    response.put("success", false);
                    response.put("message", "Etibarsız seriya nömrəsi. Zəhmət olmasa real sənəd seriyasını daxil edin.");
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                }
                if (cleanSerial.startsWith("AA")) {
                    if (!cleanSerial.matches("^AA\\d{7}$")) {
                        response.put("success", false);
                        response.put("message", "AA seriyası AA və ardınca dəqiq 7 rəqəmdən ibarət olmalıdır.");
                        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                    }
                } else if (cleanSerial.startsWith("AZE")) {
                    if (!cleanSerial.matches("^AZE\\d{8}$")) {
                        response.put("success", false);
                        response.put("message", "AZE seriyası AZE və ardınca dəqiq 8 rəqəmdən ibarət olmalıdır.");
                        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                    }
                } else {
                    if (!cleanSerial.matches("^[A-Z0-9]{7,10}$") || cleanSerial.matches("^\\d+$") || cleanSerial.matches("^[A-Z]+$")) {
                        response.put("success", false);
                        response.put("message", "Xarici sənəd nömrəsi beynəlxalq standarta uyğun olmalıdır (hərf və rəqəm, 7-10 simvol).");
                        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                    }
                }
            }

            if (finCode != null && !finCode.trim().isEmpty()) {
                String cleanFin = finCode.trim().toUpperCase().replaceAll("\\s+", "");
                if (cleanFin.length() != 7 || cleanFin.matches("^(\\w)\\1{6}$") || !cleanFin.matches("^[A-Z0-9]{7}$") || cleanFin.contains("O") || cleanFin.contains("I")) {
                    response.put("success", false);
                    response.put("message", "FİN kod dəqiq 7 simvol olmalı və O, I hərfləri daxil edilməməlidir.");
                    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
                }
            }

            String resolvedAccountType = (accountType != null && !accountType.trim().isEmpty()) ? accountType.trim().toUpperCase() : "CHECKING";
            String code = userService.initiateEmailRegistration(fullName, username, targetEmail, password, resolvedAccountType, phoneNumber, finCode, idCardSerial);

            response.put("success", true);
            response.put("email", targetEmail);
            response.put("fullName", fullName != null ? fullName.trim() : "Müştəri");
            response.put("otpCode", code);
            response.put("accountType", resolvedAccountType);
            response.put("message", "3 rəqəmli təsdiq kodu Gmail ünvanınıza göndərildi: " + targetEmail);
            response.put("username", username.trim());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", resolveCleanErrorMessage(e));
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
        }
    }

    @PostMapping(value = "/send-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtpCodeOnly(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (email == null || email.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Gmail ünvanı boş ola bilməz.");
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }
            String cleanEmail = email.trim();
            UserServiceImpl.validateEmail(cleanEmail);
            String code = emailService.prepareOtpCode(cleanEmail);
            emailService.sendOtpCode(cleanEmail, "Müştəri", code);
            response.put("success", true);
            response.put("email", cleanEmail);
            response.put("message", "3 rəqəmli təsdiq kodu Gmail ünvanınıza göndərildi: " + cleanEmail);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", resolveCleanErrorMessage(e));
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
        }
    }

    @PostMapping(value = {"/resend-code", "/register/resend-code"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resendCode(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "email", required = false) String email) {

        Map<String, Object> response = new HashMap<>();
        try {
            String identifier = (username != null && !username.trim().isEmpty())
                    ? username.trim()
                    : ((email != null && !email.trim().isEmpty()) ? email.trim() : null);

            if (identifier == null) {
                response.put("success", false);
                response.put("message", "İstifadəçi adı və ya Gmail ünvanı boş ola bilməz.");
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            int remaining = emailService.getResendCooldownRemaining(identifier);
            if (remaining > 0) {
                response.put("success", false);
                response.put("message", "Yenidən göndərmək üçün " + remaining + " saniyə gözləyin.");
                response.put("cooldownSeconds", remaining);
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
            }

            String newCode;
            if (username != null && !username.trim().isEmpty()) {
                newCode = userService.resendVerificationCode(username.trim());
            } else {
                newCode = emailService.prepareOtpCode(identifier);
                emailService.sendOtpCode(identifier, "Müştəri", newCode);
            }

            response.put("success", true);
            response.put("otpCode", newCode);
            response.put("message", "Yeni 3 rəqəmli təsdiq kodu Gmail ünvanınıza göndərildi.");
            response.put("cooldownSeconds", 30);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", resolveCleanErrorMessage(e));
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(response);
        }
    }

    @PostMapping(value = {"/register/verify-code", "/register/verify-sms"})
    public String verifyCodeAndRegister(
            HttpServletRequest request,
            @RequestParam("username") String username,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "smsCode", required = false) String smsCode,
            @RequestParam(value = "panelType", required = false) String panelType,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            String finalCode = (code != null && !code.trim().isEmpty()) ? code.trim() : (smsCode != null ? smsCode.trim() : "");
            userService.verifyAndCompleteRegistration(username, finalCode);
            redirectAttributes.addFlashAttribute("registered", true);
            redirectAttributes.addFlashAttribute("successMessage",
                    "3-digit verification code accepted! Your account has been verified and opened by Security. You may now sign in.");
            return "redirect:/login?registered=true";
        } catch (Exception e) {
            request.getSession(true);
            if ("STANDARD".equalsIgnoreCase(panelType)) {
                model.addAttribute("stdSmsSent", true);
                model.addAttribute("stdUsername", username);
            } else {
                model.addAttribute("smsSent", true);
                model.addAttribute("username", username);
            }
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        }
    }

    @PostMapping("/register/standard-customer")
    public String registerStandardCustomer(
            @RequestParam("fullName") String fullName,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            UserServiceImpl.validateFullName(fullName);
            UserServiceImpl.validateEmail(email);
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
            }
            UserServiceImpl.validatePassword(password);
            if (!password.trim().equals(confirmPassword != null ? confirmPassword.trim() : "")) {
                throw new IllegalArgumentException("Şifrələr uyğun gəlmir! Zəhmət olmasa hər iki sahəyə eyni şifrəni daxil edin.");
            }
            String cleanUsername = username.trim();
            if (isUsernameTaken(cleanUsername)) {
                throw new IllegalArgumentException(UserServiceImpl.USERNAME_TAKEN_MESSAGE);
            }
            passwordSecurityService.validatePasswordNotReused(password.trim(), cleanUsername);

            userService.initiateEmailRegistration(
                    fullName.trim(),
                    cleanUsername,
                    email.trim(),
                    password.trim(),
                    "CHECKING",
                    phoneNumber,
                    "",
                    ""
            );

            model.addAttribute("stdSmsSent", true);
            model.addAttribute("stdUsername", cleanUsername);
            model.addAttribute("stdEmail", email.trim());
            model.addAttribute("stdFullName", fullName.trim());
            model.addAttribute("stdPhoneNumber", phoneNumber);
            model.addAttribute("successMessage", "3 rəqəmli təsdiq kodu Gmail ünvanınıza göndərildi: " + email.trim());
            return "register";
        } catch (Exception e) {
            model.addAttribute("errorMessage", resolveCleanErrorMessage(e));
            model.addAttribute("stdFullName", fullName);
            model.addAttribute("stdUsername", username);
            model.addAttribute("stdEmail", email);
            model.addAttribute("stdPhoneNumber", phoneNumber);
            return "register";
        }
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam("username") String username,
                               @RequestParam("password") String password,
                               @RequestParam(value = "fullName", required = false) String fullName,
                               @RequestParam(value = "email", required = false) String email,
                               @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
                               @RequestParam(value = "code", required = false) String code,
                               @RequestParam(value = "smsCode", required = false) String smsCode,
                               @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        try {
            String finalCode = (code != null && !code.trim().isEmpty()) ? code.trim() : (smsCode != null ? smsCode.trim() : "");
            if (finalCode != null && !finalCode.isEmpty()) {
                userService.verifyAndCompleteRegistration(username, finalCode);
                redirectAttributes.addFlashAttribute("registered", true);
                return "redirect:/login?registered=true";
            }

            return "redirect:/register";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        }
    }

    @PostMapping("/register/employee")
    public String registerEmployee(
            @RequestParam("fullName") String fullName,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam(value = "employeeId", required = false) String employeeId,
            @RequestParam(value = "department", required = false, defaultValue = "") String department,
            @RequestParam(value = "position", required = false, defaultValue = "Operator") String position,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {

        try {
            if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                throw new IllegalArgumentException("İstifadəçi adı və şifrə mütləq daxil edilməlidir / Username and password are required.");
            }
            UserServiceImpl.validatePassword(password);
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Şifrələr uyğun gəlmir / Passwords do not match.");
            }

            String cleanUsername = username.trim();
            if (isUsernameTaken(cleanUsername)) {
                throw new IllegalArgumentException("Bu istifadəçi adı artıq istifadədədir, lütfən başqa istifadəçi adı seçin / This username is already taken, please choose another.");
            }

            passwordSecurityService.validatePasswordNotReused(password.trim(), cleanUsername);

            String cleanEmpId = employeeId != null && !employeeId.trim().isEmpty()
                    ? employeeId.trim().toUpperCase()
                    : "EMP-" + (int)(1000 + Math.random() * 9000);

            if (bankEmployeeRepository.existsByEmployeeId(cleanEmpId)) {
                cleanEmpId = "EMP-" + System.currentTimeMillis() % 100000;
            }

            String rawPosOrDept = (position != null && !position.trim().isEmpty()) ? position.trim() : department.trim();
            String normDept = departmentQuotaService.normalizeDepartmentName(rawPosOrDept);
            String quotaError = departmentQuotaService.checkQuotaError(normDept, null);
            if (quotaError != null) {
                throw new IllegalArgumentException(quotaError);
            }
            String cleanPosition = rawPosOrDept.isEmpty() ? departmentQuotaService.getPositionLabel(normDept) : rawPosOrDept;
            String encodedPass = passwordEncoder.encode(password.trim());

            com.financeapp.model.User user = new com.financeapp.model.User();
            user.setUsername(cleanUsername);
            user.setPassword(encodedPass);
            user.setRawPassword(password.trim());
            user.setRole("ROLE_EMPLOYEE_PENDING");
            user.setFullName(fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : cleanUsername);
            user.setEmail(email != null ? email.trim() : "");
            user.setPhoneNumber(phoneNumber != null ? phoneNumber.trim() : "");
            com.financeapp.model.User savedUser = userRepository.save(user);

            passwordSecurityService.recordPasswordUsage(cleanUsername, password.trim(), encodedPass);

            com.financeapp.model.Account empAccount = new com.financeapp.model.Account();
            empAccount.setAccountNumber(com.financeapp.service.impl.AccountServiceImpl.generate16DigitCardNumber());
            empAccount.setBalance(BigDecimal.ZERO);
            empAccount.setCurrency("EUR");
            empAccount.setAccountType("SALARY");
            empAccount.setStatus(com.financeapp.enums.AccountStatus.PENDING_VERIFICATION);
            empAccount.setUser(savedUser);
            com.financeapp.model.Account savedAccount = accountRepository.save(empAccount);

            com.financeapp.model.BankEmployee emp = new com.financeapp.model.BankEmployee(
                    savedUser.getFullName(),
                    cleanUsername,
                    savedUser.getEmail(),
                    savedUser.getPhoneNumber(),
                    cleanEmpId,
                    normDept,
                    cleanPosition
            );
            emp.setUserId(savedUser.getId());
            emp.setApprovalStatus("PENDING");
            emp.setAccountStatus("DISABLED");
            emp.setSalary(BigDecimal.ZERO);
            emp.setAccountNumber(savedAccount.getAccountNumber());
            emp.setBalance(BigDecimal.ZERO);
            bankEmployeeRepository.save(emp);

            com.financeapp.model.SecurityNotification notif = new com.financeapp.model.SecurityNotification(
                    "Yeni İş Müraciəti / Əməkdaş Qeydiyyatı",
                    String.format("👔 Yeni əməkdaş müraciəti: %s (%s, Şöbə: %s, ID: %s, Hesab: %s). Sorğu şöbəsinin ilkin yoxlaması gözlənilir.",
                            emp.getFullName(), emp.getPosition(), normDept, cleanEmpId, savedAccount.getAccountNumber()),
                    "EMPLOYEE_REGISTRATION",
                    cleanUsername
            );
            securityNotificationRepository.save(notif);

            String refNo = "JOB-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            com.financeapp.model.CustomerInquiry inq = new com.financeapp.model.CustomerInquiry(
                    cleanUsername,
                    emp.getFullName(),
                    emp.getPhoneNumber(),
                    emp.getEmail(),
                    "OTHER",
                    "İş Müraciəti: " + emp.getFullName() + " (" + cleanEmpId + ")",
                    String.format("Vəzifə: %s | Şöbə: %s | İşçi ID: %s | Hesab №: %s",
                            emp.getPosition(), normDept, cleanEmpId, savedAccount.getAccountNumber()),
                    refNo
            );
            customerInquiryRepository.save(inq);

            auditLogService.logAction(cleanUsername, "EMPLOYEE_APPLY", "EMPLOYEE", cleanEmpId,
                    "Yeni iş müraciəti göndərildi (" + normDept + " şöbəsi, Hesab: " + savedAccount.getAccountNumber() + ")");

            redirectAttributes.addFlashAttribute("registered", true);
            redirectAttributes.addFlashAttribute("successMessage",
                    "İş müraciətiniz uğurla qeydə alındı! Şəxsi bank hesabınız (" + savedAccount.getAccountNumber() + ") yaradıldı, Sorğu və Security təsdiqi gözlənilir / Job application submitted! Pending Inquiry & Security review.");
            return "redirect:/login?registered=true";

        } catch (Exception e) {
            departmentQuotaService.populateQuotaAttributes(model);
            model.addAttribute("errorMessage", resolveCleanErrorMessage(e));
            model.addAttribute("isEmployeeActive", true);
            return "register";
        }
    }

    @RequestMapping(value = "/api/auth/check-username", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUsernameAvailabilityAjax(
            @RequestParam(value = "username", required = false) String username) {
        Map<String, Object> result = new HashMap<>();
        String cleanUsername = username != null ? username.trim() : "";
        if (cleanUsername.isEmpty()) {
            result.put("exists", false);
            result.put("available", false);
            result.put("message", "İstifadəçi adı boş ola bilməz.");
            return ResponseEntity.ok(result);
        }
        boolean exists = isUsernameTaken(cleanUsername);
        result.put("exists", exists);
        result.put("available", !exists);
        result.put("message", exists ? UserServiceImpl.USERNAME_TAKEN_MESSAGE : "İstifadəçi adı uyğundur.");
        return ResponseEntity.ok(result);
    }

    private boolean isUsernameTaken(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        pendingAccountCleanupService.releasePreviouslyArchivedCredentials();
        String clean = username.trim();
        if (bankEmployeeRepository.existsByUsernameIgnoreCase(clean)) {
            return true;
        }
        com.financeapp.model.User existing = userRepository.findFirstByUsernameIgnoreCase(clean).orElse(null);
        // Hələ 3 rəqəmli Gmail kodu ilə təsdiqlənməmiş (ROLE_PENDING) hesabların istifadəçi adına qadağa qoyulmur
        return existing != null && !"ROLE_PENDING".equalsIgnoreCase(existing.getRole());
    }

    private String resolveCleanErrorMessage(Throwable e) {
        Throwable curr = e;
        boolean isDataIntegrity = false;
        while (curr != null) {
            if (curr instanceof org.springframework.dao.DataIntegrityViolationException) {
                isDataIntegrity = true;
            }
            String msg = curr.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("users_username_key")
                        || lower.contains("key (username)")
                        || lower.contains("bank_employees_username_key")) {
                    return UserServiceImpl.USERNAME_TAKEN_MESSAGE;
                }
                if (lower.contains("users_email_key") || lower.contains("key (email)")) {
                    return "Bu Gmail ünvanı artıq qeydiyyatdan keçib, lütfən başqa e-poçt ünvanı daxil edin.";
                }
                if (lower.contains("əvvəl istifadə olunub") || lower.contains("təkrar istifadə")) {
                    return com.financeapp.service.PasswordSecurityService.REUSE_ERROR_MESSAGE;
                }
            }
            curr = curr.getCause();
        }
        if (isDataIntegrity) {
            return "Daxil edilən məlumatlardan biri sistemdə artıq mövcuddur və ya tələblərə uyğun deyil.";
        }
        return e != null && e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : "Qeydiyyat zamanı gözlənilməz xəta baş verdi. Zəhmət olmasa yenidən cəhd edin.";
    }

    @PostMapping(value = "/api/auth/check-password-reuse", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkPasswordReuseAjax(
            @RequestParam("password") String password,
            @RequestParam(value = "username", required = false) String username) {
        pendingAccountCleanupService.releasePreviouslyArchivedCredentials();
        Map<String, Object> result = new HashMap<>();
        boolean reused = passwordSecurityService.isPasswordPreviouslyUsed(password, username);
        result.put("reused", reused);
        if (reused) {
            result.put("message", com.financeapp.service.PasswordSecurityService.REUSE_ERROR_MESSAGE);
        } else {
            result.put("message", "Bu şifrə unikaldır və istifadə edilə bilər.");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/api/auth/pending-otps", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<java.util.List<Map<String, String>>> getPendingOtps() {
        return ResponseEntity.ok(emailService.drainPendingOutbox());
    }

    @PostMapping(value = "/api/auth/otp-delivery-result", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportOtpDeliveryResult(
            @RequestParam("jobId") String jobId,
            @RequestParam("status") String status) {
        emailService.reportOtpDeliveryResult(jobId, status);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/api/auth/relay-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> relayOtp(
            @RequestParam("email") String email,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam("code") String code) {
        String status = emailService.verifyAndRelayOtp(email, fullName, code);
        Map<String, Object> res = new HashMap<>();
        if ("INVALID_GMAIL".equals(status)) {
            res.put("success", false);
            res.put("invalidGmail", true);
            res.put("message", EmailService.GMAIL_NOT_FOUND_MESSAGE);
            return ResponseEntity.badRequest().body(res);
        }
        res.put("success", true);
        res.put("relayed", "DELIVERED".equals(status));
        return ResponseEntity.ok(res);
    }

    private boolean isLoggedIn(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal().toString());
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority())
                            || "ADMIN".equalsIgnoreCase(a.getAuthority()));
    }
}
