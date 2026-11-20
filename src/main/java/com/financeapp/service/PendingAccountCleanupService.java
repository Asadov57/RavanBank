package com.financeapp.service;

import com.financeapp.enums.AccountStatus;
import com.financeapp.model.*;
import com.financeapp.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PendingAccountCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PendingAccountCleanupService.class);

    public static final long WARNING_THRESHOLD_MINUTES = 30;
    public static final long DELETE_THRESHOLD_MINUTES = 60;

    private final Set<Long> releasedArchivedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @PersistenceContext
    private EntityManager entityManager;

    private final SecurityRegistrationRepository securityRegistrationRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BankEmployeeRepository bankEmployeeRepository;
    private final ArchivedAccountRepository archivedAccountRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final PasswordSecurityService passwordSecurityService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;

    public PendingAccountCleanupService(SecurityRegistrationRepository securityRegistrationRepository,
                                        AccountRepository accountRepository,
                                        UserRepository userRepository,
                                        BankEmployeeRepository bankEmployeeRepository,
                                        ArchivedAccountRepository archivedAccountRepository,
                                        SecurityNotificationRepository securityNotificationRepository,
                                        PasswordSecurityService passwordSecurityService,
                                        SimpMessagingTemplate messagingTemplate,
                                        CacheManager cacheManager) {
        this.securityRegistrationRepository = securityRegistrationRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.archivedAccountRepository = archivedAccountRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.passwordSecurityService = passwordSecurityService;
        this.messagingTemplate = messagingTemplate;
        this.cacheManager = cacheManager;
    }

    private void evictAllAccountCaches() {
        if (cacheManager == null) return;
        for (String name : List.of("accounts", "accountById", "accountByNumber", "adminAccounts", "accountsByStatus")) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * Arxivdə (RavanArxiv / archived_accounts) olan bütün silinmiş hesabların aktiv cədvəllərdə
     * (account, users, security_registrations, password_history) qalmış qalıqlarını tam silir ki,
     * silinmiş hesabların həm istifadəçi adı, həm də parolu digər müştərilər tərəfindən sərbəst istifadə oluna bilsin.
     */
    @Transactional
    public void releasePreviouslyArchivedCredentials() {
        try {
            List<ArchivedAccount> archivedList = archivedAccountRepository.findAll();
            if (archivedList.isEmpty()) return;

            entityManager.flush();
            entityManager.clear();

            boolean anyPurged = false;
            for (ArchivedAccount arch : archivedList) {
                if (arch.getId() != null && releasedArchivedIds.contains(arch.getId())) {
                    continue;
                }
                String accNum = arch.getAccountNumber() != null ? arch.getAccountNumber().trim() : "";
                String uname = arch.getUsername() != null ? arch.getUsername().trim() : "";
                String rawPwd = arch.getRawPassword() != null ? arch.getRawPassword().trim() : "";

                if (!accNum.isEmpty() && !"PENDING-NO-CARD".equalsIgnoreCase(accNum)) {
                    entityManager.createNativeQuery(
                                    "DELETE FROM transactions WHERE account_id IN (SELECT id FROM account WHERE account_number = :num)")
                            .setParameter("num", accNum)
                            .executeUpdate();
                    int delAcc = entityManager.createNativeQuery(
                                    "DELETE FROM account WHERE account_number = :num")
                            .setParameter("num", accNum)
                            .executeUpdate();
                    if (delAcc > 0) {
                        anyPurged = true;
                    }
                }

                if (!uname.isEmpty() && !"N/A".equalsIgnoreCase(uname)) {
                    Number remAcc = (Number) entityManager.createNativeQuery(
                                    "SELECT count(*) FROM account a JOIN users u ON a.user_id = u.id WHERE lower(u.username) = lower(:u)")
                            .setParameter("u", uname)
                            .getSingleResult();

                    if (remAcc == null || remAcc.longValue() == 0L) {
                        entityManager.createNativeQuery(
                                        "DELETE FROM password_history WHERE lower(username) = lower(:u)")
                                .setParameter("u", uname)
                                .executeUpdate();
                        if (!rawPwd.isEmpty()) {
                            entityManager.createNativeQuery(
                                            "DELETE FROM password_history WHERE raw_password = :p")
                                    .setParameter("p", rawPwd)
                                    .executeUpdate();
                        }
                        entityManager.createNativeQuery(
                                        "DELETE FROM security_registrations WHERE lower(username) = lower(:u)")
                                .setParameter("u", uname)
                                .executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM bank_employees WHERE lower(username) = lower(:u)")
                                .setParameter("u", uname)
                                .executeUpdate();
                        entityManager.createNativeQuery(
                                        "DELETE FROM transactions WHERE user_id IN (SELECT id FROM users WHERE lower(username) = lower(:u) AND upper(role) NOT LIKE '%ADMIN%')")
                                .setParameter("u", uname)
                                .executeUpdate();
                        int delUser = entityManager.createNativeQuery(
                                        "DELETE FROM users WHERE lower(username) = lower(:u) AND upper(role) NOT LIKE '%ADMIN%'")
                                .setParameter("u", uname)
                                .executeUpdate();
                        if (delUser > 0) {
                            anyPurged = true;
                            log.info("[AsadovBank Archive] Arxivləşdirilmiş istifadəçi və şifrəsi tam azad edildi: @{}", uname);
                        }
                    }
                }
                if (arch.getId() != null) {
                    releasedArchivedIds.add(arch.getId());
                }
            }

            entityManager.flush();
            entityManager.clear();
            if (anyPurged) {
                evictAllAccountCaches();
            }
        } catch (Exception e) {
            log.warn("Arxivdəki hesabların azad edilməsi zamanı xəta: {}", e.getMessage());
        }
    }

    /**
     * Hesabı silib RavanArxiv-ə köçürür və onun username + şifrəsini digər müştərilərin
     * yenidən istifadə edə bilməsi üçün aktiv cədvəllərdən (users, account, security_registrations, password_history) birbaşa SQL ilə silir.
     */
    @Transactional
    public ArchivedAccount archiveAndPurgeAccount(Account account,
                                                  SecurityRegistration reg,
                                                  String archivedBy,
                                                  String reason) {
        User user = account != null ? account.getUser() : null;
        if (user == null && reg != null && reg.getUsername() != null) {
            user = userRepository.findFirstByUsernameIgnoreCase(reg.getUsername().trim()).orElse(null);
        }

        Long accountId = account != null ? account.getId() : null;
        Long userId = user != null ? user.getId() : null;
        boolean isAdminUser = user != null && user.getRole() != null && user.getRole().toUpperCase().contains("ADMIN");

        String username = user != null ? user.getUsername()
                : (reg != null && reg.getUsername() != null ? reg.getUsername() : "N/A");
        String fullName = user != null && user.getFullName() != null ? user.getFullName()
                : (reg != null && reg.getFullName() != null ? reg.getFullName() : username);
        String email = user != null && user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail()
                : (reg != null && reg.getEmail() != null && !reg.getEmail().isBlank() ? reg.getEmail()
                : (user != null && user.getPhoneNumber() != null ? user.getPhoneNumber() : ""));
        String rawPwd = user != null && user.getRawPassword() != null && !user.getRawPassword().isBlank() ? user.getRawPassword()
                : (reg != null && reg.getRawPassword() != null ? reg.getRawPassword() : "");
        String accNum = account != null && account.getAccountNumber() != null ? account.getAccountNumber()
                : (reg != null && reg.getAccountNumber() != null ? reg.getAccountNumber() : "PENDING-NO-CARD");
        BigDecimal balance = account != null && account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        String currency = account != null && account.getCurrency() != null ? account.getCurrency() : "AZN";
        String prevStatus = account != null && account.getStatus() != null ? account.getStatus().name()
                : (reg != null && reg.getStatus() != null ? reg.getStatus() : "PENDING_VERIFICATION");

        ArchivedAccount archived = new ArchivedAccount(
                accNum,
                username,
                fullName,
                email,
                rawPwd,
                balance,
                currency,
                archivedBy != null ? archivedBy : "Security",
                reason != null ? reason : "Hesab silinərək RavanArxiv bölməsinə köçürüldü"
        );
        archived.setPreviousStatus(prevStatus);
        ArchivedAccount savedArchived = archivedAccountRepository.saveAndFlush(archived);

        // Detach all managed entities first so Hibernate never cascades Account back from User.accounts
        entityManager.clear();

        if (accountId != null) {
            entityManager.createNativeQuery("DELETE FROM transactions WHERE account_id = :accId")
                    .setParameter("accId", accountId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM account WHERE id = :accId")
                    .setParameter("accId", accountId)
                    .executeUpdate();
        } else if (userId != null) {
            entityManager.createNativeQuery("DELETE FROM transactions WHERE user_id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM account WHERE user_id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();
        }

        if (!"N/A".equalsIgnoreCase(username)) {
            entityManager.createNativeQuery("DELETE FROM security_registrations WHERE lower(username) = lower(:u)")
                    .setParameter("u", username.trim())
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM password_history WHERE lower(username) = lower(:u)")
                    .setParameter("u", username.trim())
                    .executeUpdate();
        }
        if (rawPwd != null && !rawPwd.isBlank()) {
            entityManager.createNativeQuery("DELETE FROM password_history WHERE raw_password = :p")
                    .setParameter("p", rawPwd.trim())
                    .executeUpdate();
        }

        if (userId != null && !isAdminUser) {
            Number rem = (Number) entityManager.createNativeQuery("SELECT count(*) FROM account WHERE user_id = :uid")
                    .setParameter("uid", userId)
                    .getSingleResult();
            if (rem == null || rem.longValue() == 0L) {
                entityManager.createNativeQuery("DELETE FROM bank_employees WHERE lower(username) = lower(:u)")
                        .setParameter("u", username.trim())
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM transactions WHERE user_id = :uid")
                        .setParameter("uid", userId)
                        .executeUpdate();
                entityManager.createNativeQuery("DELETE FROM users WHERE id = :uid")
                        .setParameter("uid", userId)
                        .executeUpdate();
            }
        }

        entityManager.flush();
        entityManager.clear();
        evictAllAccountCaches();

        return savedArchived;
    }

    /**
     * Hər 15 saniyədən bir:
     * 1) Arxivə düşmüş hesabların qalıqlarını təmizləyir.
     * 2) Gmail 3 rəqəmli kodu təsdiqlənməmiş (PENDING_VERIFICATION / PENDING_SMS) hesabları yoxlayır:
     *    - 30 dəqiqə tamam olduqda Security-yə xəbərdarlıq bildirişi göndərir.
     *    - 1 saat (60 dəqiqə) tamam olduqda hesabı avtomatik silib RavanArxiv-ə köçürür və username/parolu azad edir.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void processPendingAccountsLifecycle() {
        releasePreviouslyArchivedCredentials();

        LocalDateTime now = LocalDateTime.now();
        Set<String> processedUsernames = new HashSet<>();

        List<SecurityRegistration> allRegs = securityRegistrationRepository.findAllByOrderByCreatedAtDesc();
        for (SecurityRegistration reg : allRegs) {
            if (reg == null || reg.getStatus() == null) continue;
            String st = reg.getStatus().trim().toUpperCase();
            if (!st.startsWith("PENDING")) continue;

            String uname = reg.getUsername() != null ? reg.getUsername().trim() : "";
            if (!uname.isEmpty()) {
                processedUsernames.add(uname.toLowerCase());
            }

            LocalDateTime created = reg.getCreatedAt();
            if (created == null) {
                created = now;
                reg.setCreatedAt(created);
                securityRegistrationRepository.save(reg);
            }

            long elapsedMinutes = Duration.between(created, now).toMinutes();
            User user = !uname.isEmpty() ? userRepository.findFirstByUsernameIgnoreCase(uname).orElse(null) : null;
            Account acc = (user != null) ? accountRepository.findFirstByUser(user).orElse(null) : null;

            if (elapsedMinutes >= DELETE_THRESHOLD_MINUTES) {
                String accNo = acc != null ? acc.getAccountNumber() : (reg.getAccountNumber() != null ? reg.getAccountNumber() : "PENDING");
                String fullName = reg.getFullName() != null ? reg.getFullName() : uname;
                String email = reg.getEmail() != null ? reg.getEmail() : "";

                archiveAndPurgeAccount(
                        acc,
                        reg,
                        "Security (Avto-Silinmə • 1 Saat Pending)",
                        "3 rəqəmli Gmail təsdiq kodu 1 saat ərzində təsdiqlənmədiyi (PENDING) üçün Security tərəfindən avtomatik silindi və RavanArxiv-ə köçürüldü"
                );

                SecurityNotification delNotif = new SecurityNotification(
                        "🗄️ Pending Hesab 1 Saat Sonra Silindi (RavanArxiv)",
                        String.format("🗑️ @%s (%s, Gmail: %s, Hesab: %s) 3 rəqəmli Gmail kodunu 1 saat ərzində təsdiqləmədiyi üçün Security tərəfindən avtomatik silinərək RavanArxiv bölməsinə köçürüldü. İstifadəçi adı və şifrə yenidən istifadə üçün azad edildi.",
                                uname, fullName, email, accNo),
                        "PENDING_1HR_DELETED",
                        uname
                );
                securityNotificationRepository.save(delNotif);
                broadcastAlert(delNotif);
                log.info("[AsadovBank Security] 1 saatlıq pending hesab avtomatik silindi və RavanArxiv-ə köçürüldü: @{}", uname);

            } else if (elapsedMinutes >= WARNING_THRESHOLD_MINUTES && !Boolean.TRUE.equals(reg.getWarningSent())) {
                reg.setWarningSent(true);
                securityRegistrationRepository.save(reg);
                if (acc != null) {
                    acc.setWarningSent(true);
                    accountRepository.save(acc);
                }

                String accNo = acc != null ? acc.getAccountNumber() : (reg.getAccountNumber() != null ? reg.getAccountNumber() : "PENDING");
                String fullName = reg.getFullName() != null ? reg.getFullName() : uname;
                String email = reg.getEmail() != null ? reg.getEmail() : "";
                long remainingMins = Math.max(1, DELETE_THRESHOLD_MINUTES - elapsedMinutes);

                SecurityNotification warnNotif = new SecurityNotification(
                        "⚠️ 30 Dəqiqə Xəbərdarlığı: Təsdiqlənməmiş (Pending) Hesab",
                        String.format("⏰ @%s (%s, Gmail: %s, Hesab: %s) yaradılmasından %d dəqiqə keçib, lakin 3 rəqəmli Gmail kodu hələ təsdiqlənməyib (PENDING). %d dəqiqə sonra (1 saat tamam olduqda) hesab Security tərəfindən avtomatik silinərək RavanArxiv-ə köçürüləcəkdir.",
                                uname, fullName, email, accNo, elapsedMinutes, remainingMins),
                        "PENDING_30MIN_WARNING",
                        uname
                );
                securityNotificationRepository.save(warnNotif);
                broadcastAlert(warnNotif);
                log.info("[AsadovBank Security] 30 dəqiqəlik pending hesab xəbərdarlığı göndərildi: @{}", uname);
            }
        }

        List<Account> pendingAccounts = accountRepository.findByStatus(AccountStatus.PENDING_VERIFICATION);
        for (Account acc : pendingAccounts) {
            if (acc == null) continue;
            User u = acc.getUser();
            if (u == null || u.getUsername() == null) continue;
            if (processedUsernames.contains(u.getUsername().trim().toLowerCase())) continue;

            if ("SALARY".equalsIgnoreCase(acc.getAccountType()) && bankEmployeeRepository.findByUsername(u.getUsername()).isPresent()) {
                continue;
            }

            LocalDateTime created = acc.getCreatedAt();
            if (created == null) {
                created = now;
                acc.setCreatedAt(created);
                accountRepository.save(acc);
            }

            long elapsedMinutes = Duration.between(created, now).toMinutes();
            String uname = u.getUsername();
            String fullName = u.getFullName() != null ? u.getFullName() : uname;
            String email = u.getEmail() != null ? u.getEmail() : "";
            String accNumber = acc.getAccountNumber();

            if (elapsedMinutes >= DELETE_THRESHOLD_MINUTES) {
                archiveAndPurgeAccount(
                        acc,
                        null,
                        "Security (Avto-Silinmə • 1 Saat Pending)",
                        "Pending hesab 1 saat ərzində təsdiqlənmədiyi üçün Security tərəfindən avtomatik silindi və RavanArxiv-ə köçürüldü"
                );
                SecurityNotification delNotif = new SecurityNotification(
                        "🗄️ Pending Hesab 1 Saat Sonra Silindi (RavanArxiv)",
                        String.format("🗑️ @%s (%s, Gmail: %s, Hesab: %s) 1 saat ərzində təsdiqlənmədiyi (PENDING) üçün Security tərəfindən avtomatik silinərək RavanArxiv bölməsinə köçürüldü. İstifadəçi adı və şifrə yenidən istifadə üçün azad edildi.",
                                uname, fullName, email, accNumber),
                        "PENDING_1HR_DELETED",
                        uname
                );
                securityNotificationRepository.save(delNotif);
                broadcastAlert(delNotif);
            } else if (elapsedMinutes >= WARNING_THRESHOLD_MINUTES && !Boolean.TRUE.equals(acc.getWarningSent())) {
                acc.setWarningSent(true);
                accountRepository.save(acc);
                long remainingMins = Math.max(1, DELETE_THRESHOLD_MINUTES - elapsedMinutes);

                SecurityNotification warnNotif = new SecurityNotification(
                        "⚠️ 30 Dəqiqə Xəbərdarlığı: Təsdiqlənməmiş (Pending) Hesab",
                        String.format("⏰ @%s (%s, Gmail: %s, Hesab: %s) yaradılmasından %d dəqiqə keçib və hələ də PENDING statusundadır. %d dəqiqə sonra (1 saat tamam olduqda) hesab Security tərəfindən avtomatik silinərək RavanArxiv-ə köçürüləcəkdir.",
                                uname, fullName, email, accNumber, elapsedMinutes, remainingMins),
                        "PENDING_30MIN_WARNING",
                        uname
                );
                securityNotificationRepository.save(warnNotif);
                broadcastAlert(warnNotif);
            }
        }
    }

    private void broadcastAlert(SecurityNotification notif) {
        try {
            messagingTemplate.convertAndSend("/topic/security-alerts", notif);
        } catch (Exception ignored) {}
    }
}
