package com.parkingsystem.inquiry;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Inquiry implements Serializable {

    private int inquiryId;
    private String referenceNo;
    private int userId;
    private String category;
    private String subject;
    private String description;
    private String relatedRef;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private transient InquiryState state;

    public void bindState() {
        switch (status == null ? "OPEN" : status) {
            case "IN_PROGRESS":
                state = new InProgressState();
                break;
            case "ESCALATED":
                state = new EscalatedState();
                break;
            case "RESOLVED":
                state = new ResolvedState();
                break;
            case "CLOSED":
                state = new ClosedState();
                break;
            default:
                state = new OpenState();
                break;
        }
    }

    public InquiryState getState() {
        if (state == null) {
            bindState();
        }
        return state;
    }

    public void setState(InquiryState state) {
        this.state = state;
        this.status = state.name();
    }

    public int getInquiryId() {
        return inquiryId;
    }

    public void setInquiryId(int inquiryId) {
        this.inquiryId = inquiryId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRelatedRef() {
        return relatedRef;
    }

    public void setRelatedRef(String relatedRef) {
        this.relatedRef = relatedRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.state = null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
