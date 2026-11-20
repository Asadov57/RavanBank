package com.financeapp.repository;

import com.financeapp.model.ArchivedAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivedAccountRepository extends JpaRepository<ArchivedAccount, Long> {

    List<ArchivedAccount> findAllByOrderByArchivedAtDesc();

    long count();

    boolean existsByRawPassword(String rawPassword);
}

