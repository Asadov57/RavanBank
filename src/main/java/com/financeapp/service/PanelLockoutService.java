package com.financeapp.service;

import com.financeapp.model.PanelSecurityLock;
import com.financeapp.repository.PanelSecurityLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PanelLockoutService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    private final PanelSecurityLockRepository lockRepository;

    public PanelLockoutService(PanelSecurityLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Transactional
    public LocalDateTime getActiveLockUntil(String panelKey) {
        PanelSecurityLock lock = lockRepository.findFirstByPanelKeyIgnoreCase(panelKey).orElse(null);
        if (lock == null || lock.getLockedUntil() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (lock.getLockedUntil().isAfter(now)) {
            return lock.getLockedUntil();
        }
        // Expired lock: reset cleanly for fresh 5 attempts
        lock.setFailedAttempts(0);
        lock.setLockedUntil(null);
        lock.setUpdatedAt(now);
        lockRepository.save(lock);
        return null;
    }

    @Transactional
    public int recordFailedAttempt(String panelKey) {
        PanelSecurityLock lock = lockRepository.findFirstByPanelKeyIgnoreCase(panelKey)
                .orElseGet(() -> new PanelSecurityLock(panelKey.toUpperCase()));
        int attempts = lock.getFailedAttempts() + 1;
        lock.setFailedAttempts(attempts);
        LocalDateTime now = LocalDateTime.now();
        if (attempts >= MAX_ATTEMPTS) {
            lock.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
        }
        lock.setUpdatedAt(now);
        lockRepository.save(lock);
        return attempts;
    }

    @Transactional
    public void resetFailures(String panelKey) {
        lockRepository.findFirstByPanelKeyIgnoreCase(panelKey).ifPresent(lock -> {
            if (lock.getFailedAttempts() != 0 || lock.getLockedUntil() != null) {
                lock.setFailedAttempts(0);
                lock.setLockedUntil(null);
                lock.setUpdatedAt(LocalDateTime.now());
                lockRepository.save(lock);
            }
        });
    }
}
