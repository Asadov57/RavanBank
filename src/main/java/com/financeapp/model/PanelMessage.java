package com.financeapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "panel_messages")
public class PanelMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_panel", nullable = false)
    private String fromPanel;

    @Column(name = "to_panel", nullable = false)
    private String toPanel;

    @Column(name = "sender_username", nullable = false)
    private String senderUsername;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "related_inquiry_id")
    private Long relatedInquiryId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PanelMessage() {}

    public PanelMessage(String fromPanel, String toPanel, String senderUsername,
                        String subject, String message, Long relatedInquiryId) {
        this.fromPanel = fromPanel;
        this.toPanel = toPanel;
        this.senderUsername = senderUsername;
        this.subject = subject;
        this.message = message;
        this.relatedInquiryId = relatedInquiryId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFromPanel() { return fromPanel; }
    public void setFromPanel(String fromPanel) { this.fromPanel = fromPanel; }

    public String getToPanel() { return toPanel; }
    public void setToPanel(String toPanel) { this.toPanel = toPanel; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getRelatedInquiryId() { return relatedInquiryId; }
    public void setRelatedInquiryId(Long relatedInquiryId) { this.relatedInquiryId = relatedInquiryId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
