package com.financeapp.repository;

import com.financeapp.model.BankVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankVaultRepository extends JpaRepository<BankVault, Long> {
    Optional<BankVault> findFirstByOrderByIdAsc();
}
