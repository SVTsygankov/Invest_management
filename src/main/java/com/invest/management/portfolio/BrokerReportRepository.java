package com.invest.management.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BrokerReportRepository extends JpaRepository<BrokerReport, Long> {

    List<BrokerReport> findByPortfolioOrderByReportPeriodStartDesc(Portfolio portfolio);

    Optional<BrokerReport> findByPortfolioAndReportPeriodStartAndReportPeriodEnd(
            Portfolio portfolio,
            LocalDate reportPeriodStart,
            LocalDate reportPeriodEnd);

    // Проверка пересечения периодов: новый период пересекается с существующим, если:
    // (newStart <= existingEnd) AND (newEnd >= existingStart)
    @Query("SELECT r FROM BrokerReport r WHERE r.portfolio = :portfolio " +
           "AND ((:newStart <= r.reportPeriodEnd) AND (:newEnd >= r.reportPeriodStart))")
    List<BrokerReport> findOverlappingReports(
            @Param("portfolio") Portfolio portfolio,
            @Param("newStart") LocalDate newStart,
            @Param("newEnd") LocalDate newEnd);

    // Поиск максимального периода (самого нового отчета) для портфеля
    @Query("SELECT MAX(r.reportPeriodEnd) FROM BrokerReport r WHERE r.portfolio = :portfolio")
    Optional<LocalDate> findMaxReportPeriodEnd(@Param("portfolio") Portfolio portfolio);
}

