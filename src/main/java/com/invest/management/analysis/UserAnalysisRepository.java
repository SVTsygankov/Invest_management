package com.invest.management.analysis;

import com.invest.management.user.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAnalysisRepository extends JpaRepository<UserAnalysis, Long> {

    @EntityGraph(attributePaths = {"stockAnalysis", "stockAnalysis.stock", "stockAnalysis.currentExpertAssessment"})
    List<UserAnalysis> findByUserOrderByStockAnalysisStockSecidAsc(AppUser user);

    @EntityGraph(attributePaths = {"stockAnalysis", "stockAnalysis.stock", "stockAnalysis.currentExpertAssessment"})
    Optional<UserAnalysis> findByIdAndUser(Long id, AppUser user);

    @EntityGraph(attributePaths = {"stockAnalysis", "stockAnalysis.stock"})
    Optional<UserAnalysis> findByUserAndStockAnalysis(AppUser user, StockAnalysis stockAnalysis);

    void deleteByIdAndUser(Long id, AppUser user);
}

