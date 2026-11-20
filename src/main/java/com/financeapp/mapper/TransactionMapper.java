package com.financeapp.mapper;

import org.mapstruct.Mapper;
import com.financeapp.model.Transaction;
import com.financeapp.dto.response.TransactionResponse;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionResponse toResponse(Transaction transaction);
}
