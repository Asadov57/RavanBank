package com.financeapp.service;

import java.util.List;

import com.financeapp.dto.request.AccountCreateRequest;
import com.financeapp.dto.request.DepositRequest;
import com.financeapp.dto.request.WithdrawRequest;
import com.financeapp.dto.response.AccountResponse;
import com.financeapp.enums.AccountStatus;

public interface AccountService {

    AccountResponse createAccount(AccountCreateRequest request);

    List<AccountResponse> getAllAccounts(String username);

    List<AccountResponse> getAllAccountsForAdmin();

    List<AccountResponse> getAccountsByStatus(AccountStatus status);

    AccountResponse getAccountByAccountNumber(String accountNumber);

    AccountResponse getAccountById(Long id);

    AccountResponse updateAccount(Long id, com.financeapp.dto.request.AccountUpdateRequest request);

    void deleteAccount(Long id);

    AccountResponse deposit(DepositRequest request);

    AccountResponse withdraw(WithdrawRequest request);

    com.financeapp.dto.response.TransferResponse transfer(com.financeapp.dto.request.TransferRequest request, String authenticatedUsername);

    /**
     * Şirkətə (Corporate) pul köçürməsi: şirkət hesabı bankdaxili hesabdırsa adi köçürmə edilir,
     * əks halda (xarici IBAN / hesab) vəsait göndərənin balansından komissiya ilə birlikdə çıxılır
     * və korporativ ödəniş kimi qeydə alınır.
     */
    com.financeapp.dto.response.TransferResponse corporateTransfer(com.financeapp.dto.request.TransferRequest request,
                                                                   String authenticatedUsername,
                                                                   String companyName);

    void blockAccount(Long accountId);

    void unblockAccount(Long accountId);

    long countByStatus(AccountStatus status);
}
