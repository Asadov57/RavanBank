package com.financeapp.mapper;

import com.financeapp.dto.response.TransactionResponse;
import com.financeapp.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .build();
    }
}
