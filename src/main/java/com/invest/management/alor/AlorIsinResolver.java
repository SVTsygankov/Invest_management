package com.invest.management.alor;

import com.invest.management.alor.dto.AlorPosition;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис для определения ISIN по позиции из ALOR API
 */
@Service
public class AlorIsinResolver {

    private static final Logger log = LoggerFactory.getLogger(AlorIsinResolver.class);

    private final MoexStockRepository stockRepository;
    private final BondRepository bondRepository;

    public AlorIsinResolver(MoexStockRepository stockRepository, BondRepository bondRepository) {
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
    }

    /**
     * Определяет ISIN для позиции из ALOR API
     * Использует комбинированный подход:
     * 1. Проверяет наличие isin в ответе ALOR
     * 2. Если нет - ищет по symbol в moex_stocks
     * 3. Если не найдено - ищет в moex_bonds
     * 4. Если brokerSymbol имеет формат "MOEX:SYMBOL", убирает префикс перед поиском
     * 
     * @param position позиция из ALOR API
     * @return Optional с ISIN или пустое, если не найден
     */
    public Optional<String> resolveIsin(AlorPosition position) {
        // Шаг 1: Проверяем наличие isin в ответе ALOR
        if (position.getIsin() != null && !position.getIsin().trim().isEmpty()) {
            log.debug("ISIN найден в ответе ALOR для symbol {}: {}", position.getSymbol(), position.getIsin());
            return Optional.of(position.getIsin().trim());
        }

        // Шаг 2: Извлекаем тикер из symbol или brokerSymbol
        String ticker = extractTicker(position);
        if (ticker == null || ticker.isEmpty()) {
            log.warn("Не удалось извлечь тикер для позиции: symbol={}, brokerSymbol={}", 
                    position.getSymbol(), position.getBrokerSymbol());
            return Optional.empty();
        }

        // Шаг 3: Ищем в moex_stocks
        Optional<String> isinFromStocks = stockRepository.findBySecid(ticker)
                .map(stock -> stock.getIsin())
                .filter(isin -> isin != null && !isin.trim().isEmpty());
        
        if (isinFromStocks.isPresent()) {
            log.debug("ISIN найден в moex_stocks для тикера {}: {}", ticker, isinFromStocks.get());
            return isinFromStocks;
        }

        // Шаг 4: Ищем в moex_bonds
        Optional<String> isinFromBonds = bondRepository.findBySecid(ticker)
                .map(bond -> bond.getIsin())
                .filter(isin -> isin != null && !isin.trim().isEmpty());
        
        if (isinFromBonds.isPresent()) {
            log.debug("ISIN найден в moex_bonds для тикера {}: {}", ticker, isinFromBonds.get());
            return isinFromBonds;
        }

        log.warn("ISIN не найден для тикера {} (symbol={}, brokerSymbol={})", 
                ticker, position.getSymbol(), position.getBrokerSymbol());
        return Optional.empty();
    }

    /**
     * Определяет тип инструмента (STOCK или BOND) на основе того, в какой таблице MOEX найден ISIN
     * 
     * @param position позиция из ALOR API
     * @return "STOCK" или "BOND", или null если не удалось определить
     */
    public String determineSecurityType(AlorPosition position) {
        // Сначала проверяем, есть ли isin в ответе ALOR
        if (position.getIsin() != null && !position.getIsin().trim().isEmpty()) {
            // Если есть isin, ищем в обеих таблицах
            String isin = position.getIsin().trim();
            if (stockRepository.findByIsin(isin).isPresent()) {
                return "STOCK";
            }
            if (bondRepository.findByIsin(isin).isPresent()) {
                return "BOND";
            }
        }

        // Если isin нет, используем тикер
        String ticker = extractTicker(position);
        if (ticker == null || ticker.isEmpty()) {
            return null;
        }

        // Проверяем в moex_stocks
        if (stockRepository.findBySecid(ticker).isPresent()) {
            return "STOCK";
        }

        // Проверяем в moex_bonds
        if (bondRepository.findBySecid(ticker).isPresent()) {
            return "BOND";
        }

        return null;
    }

    /**
     * Извлекает тикер из symbol или brokerSymbol
     * Если brokerSymbol имеет формат "MOEX:SYMBOL", убирает префикс
     */
    private String extractTicker(AlorPosition position) {
        // Сначала пробуем brokerSymbol
        if (position.getBrokerSymbol() != null && !position.getBrokerSymbol().trim().isEmpty()) {
            String brokerSymbol = position.getBrokerSymbol().trim();
            // Если формат "MOEX:SYMBOL", убираем префикс
            if (brokerSymbol.startsWith("MOEX:")) {
                return brokerSymbol.substring(5);
            }
            return brokerSymbol;
        }

        // Если brokerSymbol нет, используем symbol
        if (position.getSymbol() != null && !position.getSymbol().trim().isEmpty()) {
            return position.getSymbol().trim();
        }

        return null;
    }
}

