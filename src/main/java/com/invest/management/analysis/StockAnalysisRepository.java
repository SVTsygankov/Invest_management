package com.invest.management.analysis;

import com.invest.management.moex.MoexStock;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockAnalysisRepository extends JpaRepository<StockAnalysis, Long> {

    @EntityGraph(attributePaths = {"stock", "currentExpertAssessment"})
    List<StockAnalysis> findAll(Sort sort);

    @EntityGraph(attributePaths = {"stock", "currentExpertAssessment"})
    Optional<StockAnalysis> findByStock(MoexStock stock);

    @EntityGraph(attributePaths = {"stock", "currentExpertAssessment"})
    Optional<StockAnalysis> findById(Long id);
}


