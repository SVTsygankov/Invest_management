package com.invest.management.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "expert_assessment")
public class ExpertAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_analysis_id", nullable = false)
    private StockAnalysis stockAnalysis;

    @Column(name = "expert_target", precision = 18, scale = 6)
    private BigDecimal expertTarget;

    @Column(name = "expert_recommendation", length = 16)
    private String expertRecommendation;

    @Column(name = "expert_target_date", nullable = false)
    private LocalDate expertTargetDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public StockAnalysis getStockAnalysis() {
        return stockAnalysis;
    }

    public void setStockAnalysis(StockAnalysis stockAnalysis) {
        this.stockAnalysis = stockAnalysis;
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

