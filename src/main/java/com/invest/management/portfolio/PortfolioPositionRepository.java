package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {

    @EntityGraph(attributePaths = {"moexStock", "moexBond"})
    List<PortfolioPosition> findByPortfolio(Portfolio portfolio);

    @EntityGraph(attributePaths = {"moexStock", "moexBond"})
    Optional<PortfolioPosition> findByPortfolioAndIsin(Portfolio portfolio, String isin);

    @Modifying
    @Query("DELETE FROM PortfolioPosition p WHERE p.portfolio = :portfolio")
    void deleteAllByPortfolio(@Param("portfolio") Portfolio portfolio);

    /**
     * Получает все позиции всех портфелей указанных пользователей
     * Используется для массового обновления цен через ALOR API
     */
    @EntityGraph(attributePaths = {"moexStock", "moexBond", "portfolio", "portfolio.user"})
    @Query("SELECT p FROM PortfolioPosition p WHERE p.portfolio.user IN :users")
    List<PortfolioPosition> findByUsers(@Param("users") List<AppUser> users);
}

