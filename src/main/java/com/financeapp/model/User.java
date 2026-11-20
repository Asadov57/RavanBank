package com.financeapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "raw_password")
    private String rawPassword;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "fin_code", length = 10)
    private String finCode;

    @Column(name = "id_card_serial", length = 30)
    private String idCardSerial;

    @Column(name = "email")
    private String email;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    @JsonIgnore
    private List<Account> accounts = new ArrayList<>();

    public User() {}

    public User(String username, String password, String role, String fullName) {
        this.username = username;
        this.password = password;
        this.role = role != null ? role : "ROLE_USER";
        this.fullName = fullName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRawPassword() { return rawPassword; }
    public void setRawPassword(String rawPassword) { this.rawPassword = rawPassword; }

    public String getDisplayPassword() {
        if (rawPassword != null && !rawPassword.trim().isEmpty()) {
            return rawPassword;
        }
        if (password != null && !password.startsWith("$2a$") && !password.startsWith("$2b$") && !password.startsWith("$2y$")) {
            return password;
        }
        return "";
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getFinCode() { return finCode; }
    public void setFinCode(String finCode) { this.finCode = finCode; }

    public String getIdCardSerial() { return idCardSerial; }
    public void setIdCardSerial(String idCardSerial) { this.idCardSerial = idCardSerial; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public List<Account> getAccounts() { return accounts; }
    public void setAccounts(List<Account> accounts) { this.accounts = accounts; }
}
