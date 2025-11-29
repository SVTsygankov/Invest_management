package com.invest.management.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpertAssessmentRepository extends JpaRepository<ExpertAssessment, Long> {
    
    /**
     * Находит все экспертные оценки для анализа, отсортированные по дате оценки (от новых к старым)
     */
    List<ExpertAssessment> findByStockAnalysisOrderByExpertTargetDateDescCreatedAtDesc(StockAnalysis stockAnalysis);
    
    /**
     * Находит последнюю экспертной оценку для анализа
     */
    Optional<ExpertAssessment> findTopByStockAnalysisOrderByExpertTargetDateDescCreatedAtDesc(StockAnalysis stockAnalysis);
    
    /**
     * Находит все экспертные оценки для анализа по ID, отсортированные по дате оценки (от новых к старым)
     */
    List<ExpertAssessment> findByStockAnalysisIdOrderByExpertTargetDateDescCreatedAtDesc(Long stockAnalysisId);
    
    /**
     * Находит последнюю экспертной оценку для анализа по ID
     */
    Optional<ExpertAssessment> findTopByStockAnalysisIdOrderByExpertTargetDateDescCreatedAtDesc(Long stockAnalysisId);
}

