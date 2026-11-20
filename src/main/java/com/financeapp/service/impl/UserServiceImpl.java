package com.financeapp.service.impl;

import com.financeapp.dto.request.UserRegisterRequest;
import com.financeapp.enums.AccountStatus;
import com.financeapp.model.Account;
import com.financeapp.model.SecurityNotification;
import com.financeapp.model.SecurityRegistration;
import com.financeapp.model.User;
import com.financeapp.repository.AccountRepository;
import com.financeapp.repository.SecurityNotificationRepository;
import com.financeapp.repository.SecurityRegistrationRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.EmailService;
import com.financeapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.authentication.DisabledException;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    public static final String USERNAME_TAKEN_MESSAGE =
            "Bu istifadəçi adı artıq istifadədədir, lütfən başqa istifadəçi adı seçin.";

    private final UserRepository userRepository;
    private final com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.financeapp.repository.TransactionRepository transactionRepository;
    private final SecurityRegistrationRepository securityRegistrationRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final EmailService emailService;
    private final com.financeapp.service.PasswordSecurityService passwordSecurityService;

    public UserServiceImpl(UserRepository userRepository,
                           com.financeapp.repository.BankEmployeeRepository bankEmployeeRepository,
                           PasswordEncoder passwordEncoder,
                           AccountRepository accountRepository,
                           SimpMessagingTemplate messagingTemplate,
                           com.financeapp.repository.TransactionRepository transactionRepository,
                           SecurityRegistrationRepository securityRegistrationRepository,
                           SecurityNotificationRepository securityNotificationRepository,
                           EmailService emailService,
                           com.financeapp.service.PasswordSecurityService passwordSecurityService) {
        this.userRepository = userRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountRepository = accountRepository;
        this.messagingTemplate = messagingTemplate;
        this.transactionRepository = transactionRepository;
        this.securityRegistrationRepository = securityRegistrationRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.emailService = emailService;
        this.passwordSecurityService = passwordSecurityService;
    }

    public static void validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Ad və soyad daxil edilməlidir.");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Ad və soyad ən azı 2 simvoldan ibarət olmalıdır.");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.trim().length() < 4) {
            throw new IllegalArgumentException("Şifrə ən azı 4 simvoldan ibarət olmalıdır.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Gmail ünvanı mütləq daxil edilməlidir.");
        }
        String clean = email.trim().toLowerCase();
        if (!clean.contains("@") || !clean.contains(".") || clean.length() < 5) {
            throw new IllegalArgumentException("Zəhmət olmasa düzgün Gmail ünvanı daxil edin (məs: numune@gmail.com).");
        }
    }

    @Override
    @Transactional
    public void registerUser(UserRegisterRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Şifrə boş ola bilməz.");
        }

        validateFullName(request.getFullName());
        validatePassword(request.getPassword().trim());
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            validateEmail(request.getEmail().trim());
        }

        String username = request.getUsername().trim();
        if (userRepository.existsByUsernameIgnoreCase(username) || bankEmployeeRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException(USERNAME_TAKEN_MESSAGE);
        }

        passwordSecurityService.validatePasswordNotReused(request.getPassword().trim());

        String encodedPass = passwordEncoder.encode(request.getPassword().trim());
        User user = new User();
        user.setUsername(username);
        user.setPassword(encodedPass);
        user.setRawPassword(request.getPassword().trim());
        user.setRole("ROLE_USER");
        user.setFullName(request.getFullName().trim());
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        User savedUser = userRepository.save(user);
        passwordSecurityService.recordPasswordUsage(username, request.getPassword().trim(), encodedPass);

        Account account = new Account();
        account.setAccountNumber(AccountServiceImpl.generate16DigitCardNumber());
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency("AZN");
        account.setStatus(AccountStatus.ACTIVE);
        account.setUser(savedUser);
        Account savedAccount = accountRepository.save(account);

        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-INI";
        com.financeapp.model.Transaction initialTx = new com.financeapp.model.Transaction(
                new BigDecimal("1000.00"),
                "Xoş gəldin depoziti • İlkin Balans",
                "DEPOSIT",
                ref,
                savedAccount,
                savedUser
        );
        transactionRepository.save(initialTx);
    }

    @Override
    @Transactional
    public String initiateEmailRegistration(String fullName, String username, String email, String password) {
        return initiateEmailRegistration(fullName, username, email, password, "DEPOSIT", "", "");
    }

    @Override
    @Transactional
    public String initiateEmailRegistration(String fullName, String username, String email, String password,
                                            String accountType, String phoneNumber, String finCode) {
        return initiateEmailRegistration(fullName, username, email, password, accountType, phoneNumber, finCode, "");
    }

    @Override
    @Transactional
    public String initiateEmailRegistration(String fullName, String username, String email, String password,
                                            String accountType, String phoneNumber, String finCode, String idCardSerial) {
        validateFullName(fullName);
        validateEmail(email);

        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
        }
        validatePassword(password);

        String cleanUsername = username.trim();

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername) || bankEmployeeRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException(USERNAME_TAKEN_MESSAGE);
        }

        passwordSecurityService.validatePasswordNotReused(password.trim(), cleanUsername);

        String safeType = (accountType != null && !accountType.trim().isEmpty()) ? accountType.trim().toUpperCase() : "CREDIT";
        String safePhone = (phoneNumber != null && !phoneNumber.trim().isEmpty()) ? phoneNumber.trim() : "+994500000000";
        String safeFin = (finCode != null && !finCode.trim().isEmpty()) ? finCode.trim().toUpperCase() : "";
        String safeIdCard = (idCardSerial != null && !idCardSerial.trim().isEmpty()) ? idCardSerial.trim().toUpperCase() : "";

        String code = emailService.generateAndStoreOtp(cleanUsername, email.trim());

        String encodedPass = passwordEncoder.encode(password.trim());
        User user = new User();
        user.setUsername(cleanUsername);
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPhoneNumber(safePhone);
        user.setFinCode(safeFin);
        user.setIdCardSerial(safeIdCard);
        user.setPassword(encodedPass);
        user.setRawPassword(password.trim());
        user.setRole("ROLE_PENDING");
        User savedUser = userRepository.save(user);

        Account account = accountRepository.findByUserId(savedUser.getId()).orElse(new Account());
        if (account.getAccountNumber() == null) {
            account.setAccountNumber(AccountServiceImpl.generate16DigitCardNumber());
        }
        account.setBalance(new BigDecimal("0.00"));
        account.setCurrency("AZN");
        account.setAccountType(safeType);
        account.setFinCode(safeFin);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        account.setUser(savedUser);
        Account savedAccount = accountRepository.save(account);

        SecurityRegistration reg = securityRegistrationRepository
                .findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(cleanUsername)
                .orElse(new SecurityRegistration());

        reg.setFullName(fullName.trim());
        reg.setUsername(cleanUsername);
        reg.setEmail(email.trim());
        reg.setPhoneNumber(safePhone);
        reg.setFinCode(safeFin);
        reg.setIdCardSerial(safeIdCard);
        reg.setAccountType(safeType);
        reg.setRawPassword(password.trim());
        reg.setSmsCode(code);
        reg.setAccountNumber(savedAccount.getAccountNumber());
        reg.setStatus("PENDING_VERIFICATION");
        reg.setCreatedAt(LocalDateTime.now());
        securityRegistrationRepository.save(reg);

        emailService.sendOtpCode(email.trim(), fullName.trim(), code);
        log.info("[AsadovBank SMTP] Müştəri 3 rəqəmli OTP kodu Gmail-ə uğurla göndərildi: {} -> {} (Kod: {}, Növ: {})",
                cleanUsername, email.trim(), code, safeType);

        String notifMsg = String.format("🔔 %s (@%s, %s, %s) qeydiyyatdan keçir... Status: PENDING_VERIFICATION",
                fullName.trim(), cleanUsername, email.trim(), safeType);
        SecurityNotification notif = new SecurityNotification(
                "Yeni Müştəri Qeydiyyatı",
                notifMsg,
                "REGISTRATION_START",
                cleanUsername
        );
        securityNotificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception e) {
            log.warn("WebSocket göndərişi xətası (ignoring): {}", e.getMessage());
        }

        return code;
    }

    @Override
    @Transactional
    public String initiateSmsRegistration(String fullName, String username, String phoneNumber, String password) {
        if (fullName != null && !fullName.trim().isEmpty()) {
            validateFullName(fullName);
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
        }
        validatePassword(password);

        String cleanUsername = username.trim();

        if (userRepository.existsByUsernameIgnoreCase(cleanUsername) || bankEmployeeRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException(USERNAME_TAKEN_MESSAGE);
        }

        passwordSecurityService.validatePasswordNotReused(password.trim(), cleanUsername);

        int randomCode = ThreadLocalRandom.current().nextInt(100, 1000);
        String smsCode = String.valueOf(randomCode);

        SecurityRegistration reg = securityRegistrationRepository
                .findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(cleanUsername)
                .orElse(new SecurityRegistration());

        reg.setFullName(fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : cleanUsername);
        reg.setUsername(cleanUsername);
        reg.setPhoneNumber(phoneNumber != null ? phoneNumber.trim() : "");
        reg.setRawPassword(password.trim());
        reg.setSmsCode(smsCode);
        reg.setStatus("PENDING_SMS");
        reg.setCreatedAt(LocalDateTime.now());
        securityRegistrationRepository.save(reg);

        String notifMsg = String.format("🔔 %s (@%s) qeydiyyatdan keçir... SMS kodu: %s",
                reg.getFullName(), cleanUsername, smsCode);
        SecurityNotification notif = new SecurityNotification(
                "Müştəri Qeydiyyatı",
                notifMsg,
                "REGISTRATION_START",
                cleanUsername
        );
        securityNotificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception ignored) {}

        return smsCode;
    }

    @Override
    @Transactional
    public void verifyAndCompleteRegistration(String username, String smsCode) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
        }
        if (smsCode == null || smsCode.trim().isEmpty()) {
            throw new IllegalArgumentException("3 rəqəmli təsdiq kodu daxil edilməlidir.");
        }

        String cleanUsername = username.trim();
        SecurityRegistration reg = securityRegistrationRepository
                .findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(cleanUsername)
                .orElseThrow(() -> new RuntimeException("Qeydiyyat müraciəti tapılmadı: " + cleanUsername));

        boolean otpValid = emailService.verifyOtp(cleanUsername, smsCode.trim());
        if (!otpValid && (reg.getSmsCode() == null || !reg.getSmsCode().trim().equals(smsCode.trim()))) {
            throw new IllegalArgumentException("Daxil edilən 3 rəqəmli təsdiq kodu yanlışdır. Zəhmət olmasa yenidən cəhd edin.");
        }

        User user = userRepository.findFirstByUsernameIgnoreCase(cleanUsername).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(cleanUsername);
            newUser.setFullName(reg.getFullName() != null ? reg.getFullName() : cleanUsername);
            newUser.setPhoneNumber(reg.getPhoneNumber());
            newUser.setEmail(reg.getEmail());
            newUser.setFinCode(reg.getFinCode());
            newUser.setIdCardSerial(reg.getIdCardSerial());
            newUser.setPassword(passwordEncoder.encode(reg.getRawPassword()));
            newUser.setRawPassword(reg.getRawPassword());
            return newUser;
        });
        if (reg.getIdCardSerial() != null && !reg.getIdCardSerial().isEmpty()) {
            user.setIdCardSerial(reg.getIdCardSerial());
        }
        if (reg.getFinCode() != null && !reg.getFinCode().isEmpty()) {
            user.setFinCode(reg.getFinCode());
        }
        user.setRole("ROLE_USER");
        User savedUser = userRepository.save(user);
        passwordSecurityService.recordPasswordUsage(cleanUsername, savedUser.getRawPassword(), savedUser.getPassword());

        Account account = accountRepository.findByUserId(savedUser.getId()).orElseGet(() -> {
            Account newAcc = new Account();
            newAcc.setAccountNumber(reg.getAccountNumber() != null ? reg.getAccountNumber() : AccountServiceImpl.generate16DigitCardNumber());
            newAcc.setUser(savedUser);
            newAcc.setCurrency("AZN");
            return newAcc;
        });
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("0.00"));
        account.setAccountType(reg.getAccountType() != null ? reg.getAccountType() : "NORMAL");
        account.setFinCode(reg.getFinCode());
        Account savedAccount = accountRepository.save(account);

        String accTypeLabel = "CREDIT".equalsIgnoreCase(account.getAccountType())
                ? "Kredit Hesabı"
                : ("DEPOSIT".equalsIgnoreCase(account.getAccountType()) ? "Depozit Hesabı" : "Cari Hesab");

        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-INI";
        com.financeapp.model.Transaction initialTx = new com.financeapp.model.Transaction(
                new BigDecimal("0.00"),
                accTypeLabel + " Açılışı • Təsdiqlənmiş Hesab (0.00 AZN)",
                "INITIAL",
                ref,
                savedAccount,
                savedUser
        );
        transactionRepository.save(initialTx);

        reg.setStatus("ACTIVE");
        reg.setAccountNumber(savedAccount.getAccountNumber());
        reg.setVerifiedAt(LocalDateTime.now());
        securityRegistrationRepository.save(reg);

        String successMsg = String.format("✅ %s (@%s) qeydiyyatı təsdiqləndi! Hesab statusu PENDING_VERIFICATION -> ACTIVE keçirildi. İlkin balans: 0.00 AZN",
                user.getFullName(), cleanUsername);
        SecurityNotification notif = new SecurityNotification(
                "Qeydiyyat Təsdiqləndi",
                successMsg,
                "REGISTRATION_VERIFIED",
                cleanUsername
        );
        securityNotificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception ignored) {}
    }

    @Override
    @Transactional
    public String resendVerificationCode(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("İstifadəçi adı boş ola bilməz.");
        }
        String cleanUsername = username.trim();

        if (!emailService.canResend(cleanUsername)) {
            int remaining = emailService.getResendCooldownRemaining(cleanUsername);
            throw new RuntimeException("Yenidən göndərmək üçün " + remaining + " saniyə gözləyin.");
        }

        SecurityRegistration reg = securityRegistrationRepository
                .findFirstByUsernameIgnoreCaseOrderByCreatedAtDesc(cleanUsername)
                .orElseThrow(() -> new RuntimeException("Qeydiyyat müraciəti tapılmadı: " + cleanUsername));

        if (!"PENDING_VERIFICATION".equals(reg.getStatus()) && !"PENDING_SMS".equals(reg.getStatus())) {
            throw new RuntimeException("Bu hesab artıq təsdiqlənib və ya qeydiyyat prosesi tamamlanıb.");
        }

        String targetEmail = reg.getEmail();
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            throw new RuntimeException("Bu qeydiyyat üçün Gmail ünvanı tapılmadı.");
        }

        String newCode = emailService.generateAndStoreOtp(cleanUsername, targetEmail.trim());

        reg.setSmsCode(newCode);
        reg.setCreatedAt(LocalDateTime.now());
        securityRegistrationRepository.save(reg);

        emailService.sendOtpCode(targetEmail.trim(), reg.getFullName(), newCode);
        log.info("[AsadovBank RESEND] Yeni 3 rəqəmli OTP kodu yenidən göndərildi: {} -> {} (Kod: {})", cleanUsername, targetEmail, newCode);

        return newCode;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("İstifadəçi adı boş ola bilməz.");
        }
        String cleanUsername = username.trim();

        User user = userRepository.findFirstByUsernameIgnoreCase(cleanUsername)
                .or(() -> userRepository.findFirstByEmailIgnoreCase(cleanUsername))
                .or(() -> userRepository.findFirstByUsername(cleanUsername))
                .orElseThrow(() -> new UsernameNotFoundException("İstifadəçi tapılmadı: " + cleanUsername));

        if ("ROLE_PENDING".equals(user.getRole())) {
            throw new DisabledException("Hesabınız hələ təsdiqlənməyib (PENDING_VERIFICATION). Zəhmət olmasa Gmail-ə göndərilən 3 rəqəmli kod ilə qeydiyyatı təsdiqləyin.");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(user.getRole() != null ? user.getRole() : "ROLE_USER"));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }
}
