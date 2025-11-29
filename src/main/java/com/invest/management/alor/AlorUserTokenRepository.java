package com.invest.management.alor;

import com.invest.management.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlorUserTokenRepository extends JpaRepository<AlorUserToken, Long> {
    
    Optional<AlorUserToken> findByUserAndEnvironment(AppUser user, String environment);
    
    Optional<AlorUserToken> findByUserAndEnvironmentAndPortfolioId(
            AppUser user, String environment, Long portfolioId);
    
    void deleteByUserAndEnvironment(AppUser user, String environment);
}

