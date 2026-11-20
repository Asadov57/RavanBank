package com.financeapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_inquiries")
public class CustomerInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String email;

    @Column(name = "inquiry_type", nullable = false, length = 50)
    private String inquiryType = "INQUIRY";

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "reference_no", unique = true, length = 30)
    private String referenceNo;

    @Column(nullable = false, length = 30)
    private String status = "NEW";

    @Column(name = "admin_reply", columnDefinition = "TEXT")
    private String adminReply;

    @Column(name = "replied_by")
    private String repliedBy;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "forwarded_to_security")
    private Boolean forwardedToSecurity = false;

    @Column(name = "forwarded_by")
    private String forwardedBy;

    @Column(name = "forwarded_at")
    private LocalDateTime forwardedAt;

    @Column(name = "security_note", columnDefinition = "TEXT")
    private String securityNote;

    @Column(name = "loan_application_id")
    private Long loanApplicationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CustomerInquiry() {}

    public CustomerInquiry(String username, String customerName, String phoneNumber, String email,
                           String inquiryType, String subject, String message, String referenceNo) {
        this.username = username;
        this.customerName = customerName;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.inquiryType = inquiryType != null ? inquiryType : "INQUIRY";
        this.subject = subject;
        this.message = message;
        this.referenceNo = referenceNo;
        this.status = "NEW";
        this.forwardedToSecurity = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCustomerName() { return customerName; }
    public String getFullName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInquiryType() { return inquiryType; }
    public void setInquiryType(String inquiryType) { this.inquiryType = inquiryType; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminReply() { return adminReply; }
    public void setAdminReply(String adminReply) { this.adminReply = adminReply; }

    public String getRepliedBy() { return repliedBy; }
    public void setRepliedBy(String repliedBy) { this.repliedBy = repliedBy; }

    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }

    public Boolean getForwardedToSecurity() { return forwardedToSecurity; }
    public void setForwardedToSecurity(Boolean forwardedToSecurity) { this.forwardedToSecurity = forwardedToSecurity; }

    public String getForwardedBy() { return forwardedBy; }
    public void setForwardedBy(String forwardedBy) { this.forwardedBy = forwardedBy; }

    public LocalDateTime getForwardedAt() { return forwardedAt; }
    public void setForwardedAt(LocalDateTime forwardedAt) { this.forwardedAt = forwardedAt; }

    public String getSecurityNote() { return securityNote; }
    public void setSecurityNote(String securityNote) { this.securityNote = securityNote; }

    public Long getLoanApplicationId() { return loanApplicationId; }
    public void setLoanApplicationId(Long loanApplicationId) { this.loanApplicationId = loanApplicationId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

