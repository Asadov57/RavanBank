package com.financeapp.service;

import com.financeapp.dto.request.UserRegisterRequest;

public interface UserService {
    void registerUser(UserRegisterRequest request);
    String initiateSmsRegistration(String fullName, String username, String phoneNumber, String password);
    String initiateEmailRegistration(String fullName, String username, String email, String password);
    String initiateEmailRegistration(String fullName, String username, String email, String password,
                                    String accountType, String phoneNumber, String finCode);
    String initiateEmailRegistration(String fullName, String username, String email, String password,
                                    String accountType, String phoneNumber, String finCode, String idCardSerial);
    void verifyAndCompleteRegistration(String username, String smsCode);
    String resendVerificationCode(String username);
}
