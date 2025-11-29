package com.invest.management.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PortfolioCashMovementRepository extends JpaRepository<PortfolioCashMovement, Long> {

    List<PortfolioCashMovement> findByPortfolio(Portfolio portfolio);

    List<PortfolioCashMovement> findByPortfolioOrderByDateAsc(Portfolio portfolio);

    void deleteAllByPortfolio(Portfolio portfolio);

    void deleteByReport(BrokerReport report);

    @Query("SELECT COALESCE(SUM(c.creditAmount), 0) - COALESCE(SUM(c.debitAmount), 0) FROM PortfolioCashMovement c WHERE c.portfolio = :portfolio AND c.currency = :currency")
    BigDecimal calculateBalance(@Param("portfolio") Portfolio portfolio, @Param("currency") String currency);
}

