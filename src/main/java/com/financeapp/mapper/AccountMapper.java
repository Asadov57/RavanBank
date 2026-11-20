package com.financeapp.mapper;

import com.financeapp.dto.response.AccountResponse;
import com.financeapp.enums.AccountStatus;
import com.financeapp.model.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }

        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(account.getId());
        accountResponse.setAccountNumber(account.getAccountNumber());
        accountResponse.setBalance(account.getBalance());
        accountResponse.setCurrency(account.getCurrency());
        accountResponse.setStatus(account.getStatus());
        accountResponse.setAccountType(account.getAccountType());
        accountResponse.setFinCode(account.getFinCode());

        afterMapping(account, accountResponse);
        return accountResponse;
    }

    public void afterMapping(Account account, AccountResponse response) {
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
