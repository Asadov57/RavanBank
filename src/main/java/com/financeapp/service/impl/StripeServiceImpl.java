package com.financeapp.service.impl;

import com.financeapp.dto.request.DepositRequest;
import com.financeapp.dto.request.StripePaymentRequest;
import com.financeapp.dto.response.StripePaymentResponse;
import com.financeapp.model.Account;
import com.financeapp.repository.AccountRepository;
import com.financeapp.service.AccountService;
import com.financeapp.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StripeServiceImpl implements StripeService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public StripeServiceImpl(AccountRepository accountRepository,
                              AccountService accountService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    @Override
    public StripePaymentResponse createPaymentIntent(StripePaymentRequest request) {

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Hesab tapılmadı: " + request.getAccountNumber()));

        long amountInCents = request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        String description = (request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription()
                : "Hesab üzrə balans artırımı: " + account.getAccountNumber();

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(account.getCurrency().toLowerCase())
                    .setDescription(description)

                    .putMetadata("accountNumber", account.getAccountNumber())
                    .putMetadata("accountId", String.valueOf(account.getId()))

                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            return new StripePaymentResponse(
                    paymentIntent.getId(),
                    paymentIntent.getClientSecret(),
                    paymentIntent.getStatus(),
                    request.getAmount(),
                    account.getAccountNumber(),
                    "Payment Intent uğurla yaradıldı. clientSecret ilə ödənişi tamamlayın."
            );

        } catch (StripeException e) {
            throw new RuntimeException("Stripe xətası: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void confirmDepositByPaymentIntent(String paymentIntentId) {
        try {

            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            if (!"succeeded".equals(paymentIntent.getStatus())) {
                throw new RuntimeException("Ödəniş hələ tamamlanmayıb. Status: " + paymentIntent.getStatus());
            }

            String accountNumber = paymentIntent.getMetadata().get("accountNumber");
            if (accountNumber == null || accountNumber.isBlank()) {
                throw new RuntimeException("PaymentIntent metadata-sında accountNumber tapılmadı");
            }

            BigDecimal amountUsd = BigDecimal.valueOf(paymentIntent.getAmount())
                    .divide(BigDecimal.valueOf(100));

            DepositRequest depositRequest = new DepositRequest();
            depositRequest.setAccountNumber(accountNumber);
            depositRequest.setAmount(amountUsd);
            depositRequest.setDescription("Stripe Ödənişi • PaymentIntent: " + paymentIntentId);

            accountService.deposit(depositRequest);

        } catch (StripeException e) {
            throw new RuntimeException("Stripe retrieve xətası: " + e.getMessage(), e);
        }
    }
}
