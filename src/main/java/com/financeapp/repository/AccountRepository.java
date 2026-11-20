package com.financeapp.repository;

import com.financeapp.enums.AccountStatus;
import com.financeapp.model.Account;
import com.financeapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByUserId(Long userId);

    Optional<Account> findFirstByUser(User user);

    Optional<Account> findFirstByUserUsername(String username);

    List<Account> findByUser(User user);

    List<Account> findByUserUsername(String username);

    List<Account> findByStatus(AccountStatus status);

    long countByStatus(AccountStatus status);
}
