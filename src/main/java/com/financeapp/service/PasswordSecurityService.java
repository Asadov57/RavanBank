package com.financeapp.service;

import com.financeapp.model.PasswordHistory;
import com.financeapp.model.User;
import com.financeapp.repository.PasswordHistoryRepository;
import com.financeapp.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PasswordSecurityService {

    public static final String REUSE_ERROR_MESSAGE =
            "Bu şifrə hazırda aktiv və ya gözləmədə olan başqa bir hesab tərəfindən istifadə olunur! Yalnız Arxivə (RavanArxiv) silinmiş hesabların şifrə və istifadəçi adları yenidən istifadə edilə bilər.";

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordSecurityService(PasswordHistoryRepository passwordHistoryRepository,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder) {
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isPasswordPreviouslyUsed(String rawPassword) {
        return isPasswordPreviouslyUsed(rawPassword, null);
    }

    /**
     * Arxivdə olmayan (aktiv və ya pending olaraq users cədvəlində mövcud olan) hesabların şifrəsini
     * heç kim təkrar istifadə edə bilməz. Hesab silinib RavanArxiv-ə düşdükdə isə users cədvəlindən
     * təmizləndiyi üçün həmin şifrəni bütün istifadəçilər yenidən işlədə bilər.
     */
    public boolean isPasswordPreviouslyUsed(String rawPassword, String excludePendingUsername) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            return false;
        }
        String candidate = rawPassword.trim();
        String exclude = (excludePendingUsername != null && !excludePendingUsername.trim().isEmpty())
                ? excludePendingUsername.trim()
                : null;

        List<User> activeOrPendingUsers = userRepository.findAll();
        for (User u : activeOrPendingUsers) {
            if (u == null) continue;
            // Hələ 3 rəqəmli Gmail kodu təsdiqlənməmiş (kod çatmamış və ya Gmail-i səhv olmuş) hesabların şifrəsinə qadağa qoyulmur
            if ("ROLE_PENDING".equalsIgnoreCase(u.getRole())) {
                continue;
            }
            if (exclude != null && u.getUsername() != null && u.getUsername().equalsIgnoreCase(exclude)) {
                continue;
            }
            if (u.getRawPassword() != null && !u.getRawPassword().isEmpty()) {
                if (u.getRawPassword().trim().equals(candidate)) {
                    return true;
                }
            } else if (u.getPassword() != null && !u.getPassword().isEmpty()) {
                try {
                    if (passwordEncoder.matches(candidate, u.getPassword())) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public void validatePasswordNotReused(String rawPassword) {
        validatePasswordNotReused(rawPassword, null);
    }

    public void validatePasswordNotReused(String rawPassword, String excludePendingUsername) {
        if (isPasswordPreviouslyUsed(rawPassword, excludePendingUsername)) {
            throw new IllegalArgumentException(REUSE_ERROR_MESSAGE);
        }
    }

    @Transactional
    public void recordPasswordUsage(String username, String rawPassword, String encodedPassword) {
        if (username == null || rawPassword == null || rawPassword.trim().isEmpty()) {
            return;
        }
        String cleanUser = username.trim();
        String cleanRaw = rawPassword.trim();
        List<PasswordHistory> existing = passwordHistoryRepository.findByUsernameIgnoreCaseOrderByCreatedAtDesc(cleanUser);
        for (PasswordHistory ph : existing) {
            if (cleanRaw.equals(ph.getRawPassword())) {
                return;
            }
        }
        passwordHistoryRepository.save(new PasswordHistory(cleanUser, cleanRaw, encodedPassword != null ? encodedPassword : cleanRaw));
    }

    @Transactional
    public void releaseCredentialsForArchivedUser(String username, String rawPassword) {
        if (username != null && !username.trim().isEmpty()) {
            List<PasswordHistory> byUser = passwordHistoryRepository.findByUsernameIgnoreCaseOrderByCreatedAtDesc(username.trim());
            if (!byUser.isEmpty()) {
                passwordHistoryRepository.deleteAll(byUser);
            }
        }
        if (rawPassword != null && !rawPassword.trim().isEmpty()) {
            List<PasswordHistory> byPwd = passwordHistoryRepository.findByRawPassword(rawPassword.trim());
            if (!byPwd.isEmpty()) {
                passwordHistoryRepository.deleteAll(byPwd);
            }
        }
    }

    @Transactional
    public void clearAllPasswordHistory() {
        passwordHistoryRepository.deleteAllInBatch();
    }
}
