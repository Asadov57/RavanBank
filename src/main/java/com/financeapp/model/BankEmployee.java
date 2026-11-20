package com.financeapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_employees")
public class BankEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "employee_id", nullable = false, unique = true, length = 50)
    private String employeeId;

    @Column(name = "employee_code", length = 100)
    private String employeeCode;

    @Column(length = 100)
    private String department = "Kredit Şöbəsi";

    @Column(length = 100)
    private String position = "Kredit Mütəxəssisi";

    @Column(nullable = false, length = 50)
    private String role = "ROLE_EMPLOYEE";

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "PENDING";

    @Column(name = "account_status", nullable = false, length = 30)
    private String accountStatus = "DISABLED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(precision = 19, scale = 2)
    private BigDecimal salary;

    @Column(name = "salary_currency", length = 10)
    private String salaryCurrency = "AZN";

    @Column(name = "last_salary_paid_at")
    private LocalDateTime lastSalaryPaidAt;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "escalated_to_admin")
    private Boolean escalatedToAdmin = false;

    @Column(name = "escalation_note", columnDefinition = "TEXT")
    private String escalationNote;

    @Column(name = "escalated_by", length = 100)
    private String escalatedBy;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    public BankEmployee() {}

    public BankEmployee(String fullName, String username, String email, String phoneNumber,
                        String employeeId, String department, String position) {
        this(fullName, username, email, phoneNumber, employeeId, null, department, position);
    }

    public BankEmployee(String fullName, String username, String email, String phoneNumber,
                        String employeeId, String employeeCode, String department, String position) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.department = department != null && !department.trim().isEmpty() ? department.trim() : "Müştəri Xidmətləri";
        this.position = position != null && !position.trim().isEmpty() ? position.trim() : "Bank Mütəxəssisi";
        this.role = "ROLE_EMPLOYEE";
        this.approvalStatus = "PENDING";
        this.accountStatus = "DISABLED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }

    public String getSalaryCurrency() { return salaryCurrency != null && !salaryCurrency.isBlank() ? salaryCurrency : "AZN"; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }

    public LocalDateTime getLastSalaryPaidAt() { return lastSalaryPaidAt; }
    public void setLastSalaryPaidAt(LocalDateTime lastSalaryPaidAt) { this.lastSalaryPaidAt = lastSalaryPaidAt; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getBalance() { return balance != null ? balance : BigDecimal.ZERO; }
    public void setBalance(BigDecimal balance) { this.balance = balance != null ? balance : BigDecimal.ZERO; }

    public Boolean getEscalatedToAdmin() { return Boolean.TRUE.equals(escalatedToAdmin); }
    public void setEscalatedToAdmin(Boolean escalatedToAdmin) { this.escalatedToAdmin = Boolean.TRUE.equals(escalatedToAdmin); }

    public String getEscalationNote() { return escalationNote; }
    public void setEscalationNote(String escalationNote) { this.escalationNote = escalationNote; }

    public String getEscalatedBy() { return escalatedBy; }
    public void setEscalatedBy(String escalatedBy) { this.escalatedBy = escalatedBy; }

    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }

    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return "**** **** **** 1550";
        }
        String digits = accountNumber.replaceAll("\\s+", "");
        if (digits.length() >= 4) {
            return "**** **** **** " + digits.substring(digits.length() - 4);
        }
        return "**** **** **** " + digits;
    }

    public boolean isBlocked() {
        return "BLOCKED".equalsIgnoreCase(this.accountStatus);
    }
}

