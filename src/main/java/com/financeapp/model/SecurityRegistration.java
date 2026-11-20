package com.financeapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_registrations")
public class SecurityRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(nullable = false)
    private String username;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "fin_code", length = 10)
    private String finCode;

    @Column(name = "id_card_serial", length = 30)
    private String idCardSerial;

    @Column(name = "account_type", length = 30)
    private String accountType = "DEPOSIT";

    @Column(name = "email")
    private String email;

    private String rawPassword;

    @Column(nullable = false, length = 10)
    private String smsCode;

    @Column(nullable = false)
    private String status = "PENDING_SMS";

    private String accountNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "warning_sent")
    private Boolean warningSent = false;

    public SecurityRegistration() {}

    public SecurityRegistration(String fullName, String username, String phoneNumber,
                                String rawPassword, String smsCode) {
        this.fullName = fullName;
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.rawPassword = rawPassword;
        this.smsCode = smsCode;
        this.status = "PENDING_SMS";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getFinCode() { return finCode; }
    public void setFinCode(String finCode) { this.finCode = finCode; }

    public String getIdCardSerial() { return idCardSerial; }
    public void setIdCardSerial(String idCardSerial) { this.idCardSerial = idCardSerial; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRawPassword() { return rawPassword; }
    public void setRawPassword(String rawPassword) { this.rawPassword = rawPassword; }

    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public Boolean getWarningSent() { return warningSent; }
    public void setWarningSent(Boolean warningSent) { this.warningSent = warningSent; }
}

