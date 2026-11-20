package com.financeapp.service;

import com.financeapp.dto.request.TransactionCreateRequest;
import com.financeapp.dto.response.TransactionResponse;

import java.util.List;

public interface TransactionService {

    TransactionResponse createTransaction(TransactionCreateRequest request);

    TransactionResponse getTransactionById(Long id);
    List<TransactionResponse> getTransactionsByUsername(String username);
    List<TransactionResponse> getTransactionsByAccountId(Long accountId);
    List<TransactionResponse> getAllTransactions();

    void deleteTransaction(Long id);
}
