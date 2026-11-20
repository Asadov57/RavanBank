package com.financeapp.repository;

import com.financeapp.model.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findAllByOrderByCreatedAtDesc();
    List<PasswordHistory> findByUsernameIgnoreCaseOrderByCreatedAtDesc(String username);
    boolean existsByRawPassword(String rawPassword);
    List<PasswordHistory> findByRawPassword(String rawPassword);
    List<PasswordHistory> findByRawPasswordIsNull();
}
