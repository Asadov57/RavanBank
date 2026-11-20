package com.financeapp.mapper;

import com.financeapp.dto.response.AccountResponse;
import com.financeapp.enums.AccountStatus;
import com.financeapp.model.Account;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "accountNumber", source = "accountNumber")
    @Mapping(target = "balance", source = "balance")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "cvv", ignore = true)
    @Mapping(target = "expiryDate", ignore = true)
    @Mapping(target = "transferEligible", ignore = true)
    @Mapping(target = "accountType", source = "accountType")
    @Mapping(target = "finCode", source = "finCode")
    AccountResponse toResponse(Account account);

    @AfterMapping
    default void afterMapping(Account account, @MappingTarget AccountResponse response) {
        if (account.getUser() != null) {
            String uName = account.getUser().getUsername();
            String fName = account.getUser().getFullName();
            response.setCustomerName((fName != null && !fName.trim().isEmpty()) ? fName : uName);
            response.setUsername(uName);
            response.setPassword(account.getUser().getDisplayPassword());
        }
        response.setTransferEligible(account.getStatus() == AccountStatus.ACTIVE);
        if (account.getAccountType() != null) {
            response.setAccountType(account.getAccountType());
        }
        if (account.getFinCode() != null) {
            response.setFinCode(account.getFinCode());
        }
    }
}
