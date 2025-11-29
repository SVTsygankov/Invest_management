package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUser(AppUser user);

    Optional<Portfolio> findByIdAndUser(Long id, AppUser user);
}

