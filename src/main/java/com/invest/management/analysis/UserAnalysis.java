package com.invest.management.analysis;

import com.invest.management.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_analysis", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "stock_analysis_id"})
})
public class UserAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_analysis_id", nullable = false)
    private StockAnalysis stockAnalysis;

    @Column(precision = 18, scale = 4)
    private BigDecimal support;

    @Column(name = "support_updated_at")
    private OffsetDateTime supportUpdatedAt;

    @Column(precision = 18, scale = 4)
    private BigDecimal resistance;

    @Column(name = "resistance_updated_at")
    private OffsetDateTime resistanceUpdatedAt;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public StockAnalysis getStockAnalysis() {
        return stockAnalysis;
    }

    public void setStockAnalysis(StockAnalysis stockAnalysis) {
        this.stockAnalysis = stockAnalysis;
    }

    public BigDecimal getSupport() {
        return support;
    }

    public void setSupport(BigDecimal support) {
        this.support = support;
    }

    public OffsetDateTime getSupportUpdatedAt() {
        return supportUpdatedAt;
    }

    public void setSupportUpdatedAt(OffsetDateTime supportUpdatedAt) {
        this.supportUpdatedAt = supportUpdatedAt;
    }

    public BigDecimal getResistance() {
        return resistance;
    }

    public void setResistance(BigDecimal resistance) {
        this.resistance = resistance;
    }

    public OffsetDateTime getResistanceUpdatedAt() {
        return resistanceUpdatedAt;
    }

    public void setResistanceUpdatedAt(OffsetDateTime resistanceUpdatedAt) {
        this.resistanceUpdatedAt = resistanceUpdatedAt;
    }
}

