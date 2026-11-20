package com.financeapp.repository;

import com.financeapp.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByUsernameOrderByCreatedAtDesc(String username);
    List<LoanApplication> findAllByOrderByCreatedAtDesc();
    List<LoanApplication> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}

