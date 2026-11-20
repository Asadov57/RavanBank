package com.financeapp.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class CurrencyConverter {

    public static final BigDecimal USD_TO_AZN = new BigDecimal("1.70");
    public static final BigDecimal EUR_TO_AZN = new BigDecimal("1.85");
    public static final BigDecimal AZN_TO_AZN = BigDecimal.ONE;

    private CurrencyConverter() {}

    public static String normalize(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return "AZN";
        }
        String upper = currency.trim().toUpperCase();
        if ("USD".equals(upper) || "EUR".equals(upper) || "AZN".equals(upper)) {
            return upper;
        }
        return "AZN";
    }

    public static BigDecimal toAznRate(String currency) {
        String c = normalize(currency);
        return switch (c) {
            case "USD" -> USD_TO_AZN;
            case "EUR" -> EUR_TO_AZN;
            default -> AZN_TO_AZN;
        };
    }

    public static BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal inAzn = amount.multiply(toAznRate(from));
        return inAzn.divide(toAznRate(to), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (from.equals(to)) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return toAznRate(from).divide(toAznRate(to), 4, RoundingMode.HALF_UP);
    }

    public static String symbol(String currency) {
        String c = normalize(currency);
        return switch (c) {
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> "₼";
        };
    }
}
