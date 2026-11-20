package com.financeapp.repository;

import com.financeapp.model.Account;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserOrderByTimestampDesc(User user);
    List<Transaction> findByAccountOrderByTimestampDesc(Account account);
    List<Transaction> findAllByOrderByTimestampDesc();
}
