package com.financeapp.service.impl;

import com.financeapp.dto.request.TransactionCreateRequest;
import com.financeapp.dto.response.TransactionResponse;
import com.financeapp.mapper.TransactionMapper;
import com.financeapp.model.Account;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.AccountRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.TransactionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionMapper transactionMapper;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                   AccountRepository accountRepository,
                                   UserRepository userRepository,
                                   TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"transactions", "transactionsByAccount", "allTransactions"}, allEntries = true)
    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + request.getAccountId()));

        String ref = "TX-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-MAN";
        String memo = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription() : request.getType();

        Transaction tx = new Transaction(
                request.getAmount(),
                memo,
                request.getType().toUpperCase(),
                ref,
                account,
                account.getUser()
        );
        Transaction saved = transactionRepository.save(tx);
        return transactionMapper.toResponse(saved);
    }

    @Override
    @Cacheable(value = "transactionById", key = "#id")
    public TransactionResponse getTransactionById(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Əməliyyat tapılmadı. ID: " + id));
        return transactionMapper.toResponse(tx);
    }

    @Override
    @Cacheable(value = "transactions", key = "#username")
    public List<TransactionResponse> getTransactionsByUsername(String username) {
        User user = userRepository.findFirstByUsername(username)
                .orElseThrow(() -> new RuntimeException("İstifadəçi tapılmadı: " + username));
        return transactionRepository.findByUserOrderByTimestampDesc(user).stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "transactionsByAccount", key = "#accountId")
    public List<TransactionResponse> getTransactionsByAccountId(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı. ID: " + accountId));
        return transactionRepository.findByAccountOrderByTimestampDesc(account).stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "allTransactions")
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"transactionById", "transactions", "transactionsByAccount", "allTransactions"},
            allEntries = true)
    public void deleteTransaction(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new RuntimeException("Əməliyyat tapılmadı. ID: " + id);
        }
        transactionRepository.deleteById(id);
    }
}
