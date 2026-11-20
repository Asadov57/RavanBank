package com.financeapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public class UserRegisterRequest {

    private String fullName;

    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    private String phoneNumber;
    private String email;
    private String smsCode;
    private String accountType;
    private String finCode;
    private String idCardSerial;

    public UserRegisterRequest() {}

    public UserRegisterRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public UserRegisterRequest(String fullName, String username, String email, String password, String smsCode, boolean isEmail) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        this.smsCode = smsCode;
    }

    public UserRegisterRequest(String fullName, String username, String phoneNumber, String password, String smsCode) {
        this.fullName = fullName;
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.password = password;
        this.smsCode = smsCode;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getFinCode() { return finCode; }
    public void setFinCode(String finCode) { this.finCode = finCode; }

    public String getIdCardSerial() { return idCardSerial; }
    public void setIdCardSerial(String idCardSerial) { this.idCardSerial = idCardSerial; }
}
