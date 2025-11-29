package com.invest.management.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {

    List<PortfolioTransaction> findByPortfolioOrderByTradeDateAsc(Portfolio portfolio);

    @Query("SELECT t FROM PortfolioTransaction t WHERE t.portfolio = :portfolio AND t.tradeDate BETWEEN :startDate AND :endDate ORDER BY t.tradeDate ASC")
    List<PortfolioTransaction> findByPortfolioAndPeriod(@Param("portfolio") Portfolio portfolio,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    Optional<PortfolioTransaction> findByPortfolioAndTradeNumber(Portfolio portfolio, String tradeNumber);
}

