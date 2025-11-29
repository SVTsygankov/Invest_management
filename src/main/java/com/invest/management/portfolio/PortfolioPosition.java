package com.invest.management.portfolio;

import com.invest.management.moex.MoexStock;
import com.invest.management.moex.bond.Bond;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "portfolio_positions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "isin"}))
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 12)
    private String isin;

    @Column(name = "security_type", length = 10)
    private String securityType; // STOCK or BOND

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moex_stock_id")
    private MoexStock moexStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moex_bond_id")
    private Bond moexBond;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "average_purchase_price", precision = 18, scale = 6)
    private BigDecimal averagePurchasePrice;

    @Column(name = "last_known_price", precision = 18, scale = 6)
    private BigDecimal lastKnownPrice;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void prePersist() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public MoexStock getMoexStock() {
        return moexStock;
    }

    public void setMoexStock(MoexStock moexStock) {
        this.moexStock = moexStock;
    }

    public Bond getMoexBond() {
        return moexBond;
    }

    public void setMoexBond(Bond moexBond) {
        this.moexBond = moexBond;
    }

    /**
     * Получает название инструмента из MOEX справочника или возвращает ISIN
     */
    public String getSecurityName() {
        if (moexStock != null && moexStock.getShortname() != null) {
            return moexStock.getShortname();
        }
        if (moexBond != null && moexBond.getShortname() != null) {
            return moexBond.getShortname();
        }
        return isin;
    }

    /**
     * Получает валюту из MOEX справочника
     */
    public String getCurrency() {
        if (moexStock != null && moexStock.getCurrencyid() != null) {
            return moexStock.getCurrencyid();
        }
        if (moexBond != null && moexBond.getCurrencyid() != null) {
            return moexBond.getCurrencyid();
        }
        return null;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    public void setAveragePurchasePrice(BigDecimal averagePurchasePrice) {
        this.averagePurchasePrice = averagePurchasePrice;
    }

    public BigDecimal getLastKnownPrice() {
        return lastKnownPrice;
    }

    public void setLastKnownPrice(BigDecimal lastKnownPrice) {
        this.lastKnownPrice = lastKnownPrice;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

