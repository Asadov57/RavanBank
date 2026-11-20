package com.financeapp.repository;

import com.financeapp.model.VaultTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VaultTransactionRepository extends JpaRepository<VaultTransaction, Long> {
    List<VaultTransaction> findAllByOrderByTimestampDesc();
}
