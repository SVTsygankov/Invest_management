package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public List<Portfolio> getUserPortfolios(AppUser user) {
        return portfolioRepository.findByUser(user);
    }

    public Optional<Portfolio> getPortfolioByIdAndUser(Long id, AppUser user) {
        return portfolioRepository.findByIdAndUser(id, user);
    }

    @Transactional
    public Portfolio createPortfolio(AppUser user, String name, String brokerAccount) {
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        portfolio.setName(name);
        portfolio.setBrokerAccount(brokerAccount);
        return portfolioRepository.save(portfolio);
    }

    @Transactional
    public void deletePortfolio(Long portfolioId, AppUser user) {
        Optional<Portfolio> portfolio = portfolioRepository.findByIdAndUser(portfolioId, user);
        if (portfolio.isPresent()) {
            portfolioRepository.delete(portfolio.get());
        } else {
            throw new IllegalArgumentException("Портфель не найден или не принадлежит пользователю");
        }
    }
}

