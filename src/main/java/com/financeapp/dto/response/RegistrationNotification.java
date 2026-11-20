package com.financeapp.dto.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RegistrationNotification {

    private String username;
    private String accountNumber;
    private String timestamp;
    private String message;

    public RegistrationNotification() {}

    public RegistrationNotification(String username, String accountNumber) {
        this.username = username;
        this.accountNumber = accountNumber;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        this.message = "New customer registered: " + username;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
