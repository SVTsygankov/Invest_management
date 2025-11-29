package com.invest.management.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class ExpertAssessmentDto {
    private Long id;
    private BigDecimal expertTarget;
    private String expertRecommendation;
    private LocalDate expertTargetDate;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getExpertTarget() {
        return expertTarget;
    }

    public void setExpertTarget(BigDecimal expertTarget) {
        this.expertTarget = expertTarget;
    }

    public String getExpertRecommendation() {
        return expertRecommendation;
    }

    public void setExpertRecommendation(String expertRecommendation) {
        this.expertRecommendation = expertRecommendation;
    }

    public LocalDate getExpertTargetDate() {
        return expertTargetDate;
    }

    public void setExpertTargetDate(LocalDate expertTargetDate) {
        this.expertTargetDate = expertTargetDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

