package com.invest.management.analysis;

import com.invest.management.moex.MoexStock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_analysis")
public class StockAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true)
    private MoexStock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_assessment_id")
    private ExpertAssessment currentExpertAssessment;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public MoexStock getStock() {
        return stock;
    }

    public void setStock(MoexStock stock) {
        this.stock = stock;
    }

    public ExpertAssessment getCurrentExpertAssessment() {
        return currentExpertAssessment;
    }

    public void setCurrentExpertAssessment(ExpertAssessment currentExpertAssessment) {
        this.currentExpertAssessment = currentExpertAssessment;
    }

    /**
     * Получает экспертную целевую цену из текущей оценки (для обратной совместимости)
     */
    @Transient
    public BigDecimal getExpertTarget() {
        return currentExpertAssessment != null ? currentExpertAssessment.getExpertTarget() : null;
    }

    /**
     * Получает экспертную рекомендацию из текущей оценки (для обратной совместимости)
     */
    @Transient
    public String getExpertRecommendation() {
        return currentExpertAssessment != null ? currentExpertAssessment.getExpertRecommendation() : null;
    }

    /**
     * Получает дату обновления экспертной оценки (для обратной совместимости)
     */
    @Transient
    public OffsetDateTime getExpertTargetUpdatedAt() {
        return currentExpertAssessment != null ? currentExpertAssessment.getCreatedAt() : null;
    }

    /**
     * Получает дату экспертной оценки (expert_target_date)
     */
    @Transient
    public LocalDate getExpertTargetDate() {
        return currentExpertAssessment != null ? currentExpertAssessment.getExpertTargetDate() : null;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}


