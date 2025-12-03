package com.invest.management.alor;

import com.invest.management.alor.common.AlorApiClient;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondRepository;
import com.invest.management.portfolio.PortfolioPosition;
import com.invest.management.portfolio.PortfolioPositionRepository;
import com.invest.management.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для периодического обновления цен позиций из ALOR API
 */
@Service
@ConditionalOnProperty(name = "alor.price-updater.enabled", havingValue = "true", matchIfMissing = false)
public class AlorPriceUpdater {

    private static final Logger log = LoggerFactory.getLogger(AlorPriceUpdater.class);

    private final AlorUserTokenRepository tokenRepository;
    private final AlorApiClient alorApiClient;
    private final PortfolioPositionRepository positionRepository;
    private final MoexStockRepository stockRepository;
    private final BondRepository bondRepository;

    public AlorPriceUpdater(AlorUserTokenRepository tokenRepository,
                           AlorApiClient alorApiClient,
                           PortfolioPositionRepository positionRepository,
                           MoexStockRepository stockRepository,
                           BondRepository bondRepository) {
        this.tokenRepository = tokenRepository;
        this.alorApiClient = alorApiClient;
        this.positionRepository = positionRepository;
        this.stockRepository = stockRepository;
        this.bondRepository = bondRepository;
    }

    /**
     * Обновляет цены позиций из ALOR API для всех портфелей пользователей с токенами
     * Использует API получения котировок по тикерам для оптимизации запросов
     * Запускается по расписанию, определенному в application.properties
     */
    @Scheduled(cron = "${alor.price-updater.cron:0 * * * * *}")
    @Transactional
    public void updatePricesFromAlor() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Начало обновления цен позиций из ALOR API (по тикерам)");
        log.info("═══════════════════════════════════════════════════════════");

        try {
            // Получаем все токены (по пользователям, не по портфелям)
            List<AlorUserToken> allTokens = tokenRepository.findAll();
            
            if (allTokens.isEmpty()) {
                log.info("Не найдено ALOR токенов");
                return;
            }

            // Группируем токены по пользователю и окружению (предпочитаем production)
            Map<AppUser, AlorUserToken> tokensByUser = allTokens.stream()
                    .collect(Collectors.toMap(
                            AlorUserToken::getUser,
                            token -> token,
                            (existing, replacement) -> {
                                // Если есть несколько токенов для одного пользователя, предпочитаем production
                                if ("production".equals(replacement.getEnvironment())) {
                                    return replacement;
                                }
                                if ("production".equals(existing.getEnvironment())) {
                                    return existing;
                                }
                                // Если оба test или оба production, берем последний
                                return replacement;
                            }));

            List<AppUser> usersWithTokens = new ArrayList<>(tokensByUser.keySet());
            log.info("Найдено {} уникальных пользователей с ALOR токенами", usersWithTokens.size());

            // Получаем все позиции всех портфелей пользователей с токенами
            List<PortfolioPosition> allPositions = positionRepository.findByUsers(usersWithTokens);
            log.info("Найдено {} позиций во всех портфелях пользователей с токенами", allPositions.size());

            if (allPositions.isEmpty()) {
                log.info("Нет позиций для обновления");
                return;
            }

            // Собираем уникальные тикеры (exchange + symbol) и группируем позиции по ним
            // Ключ: "exchange:symbol", значение: список позиций с этим тикером
            Map<String, List<PortfolioPosition>> positionsByTicker = new HashMap<>();
            Map<String, String> exchangeByTicker = new HashMap<>(); // Для хранения биржи для каждого тикера
            
            for (PortfolioPosition position : allPositions) {
                String ticker = getTickerFromPosition(position);
                if (ticker == null || ticker.isEmpty()) {
                    log.debug("Не удалось определить тикер для позиции ISIN: {}", position.getIsin());
                    continue;
                }

                String exchange = "MOEX"; // По умолчанию MOEX, можно расширить
                String tickerKey = exchange + ":" + ticker;
                
                positionsByTicker.computeIfAbsent(tickerKey, k -> new ArrayList<>()).add(position);
                exchangeByTicker.put(tickerKey, exchange);
            }

            log.info("Собрано {} уникальных тикеров для обновления цен", positionsByTicker.size());

            // Используем токен первого пользователя для запросов к ALOR API
            // (все пользователи с токенами могут использовать один токен для получения котировок)
            AppUser firstUser = usersWithTokens.get(0);
            AlorUserToken firstToken = tokensByUser.get(firstUser);
            String environment = firstToken.getEnvironment();

            // Получаем Access Token один раз перед циклом (кэширование)
            log.info("Получение Access Token для пользователя {} (окружение: {})", firstUser.getEmail(), environment);
            Optional<String> accessTokenOpt = alorApiClient.getAccessTokenForUser(firstUser, environment);
            if (accessTokenOpt.isEmpty()) {
                log.warn("⚠ Не удалось получить Access Token для пользователя {}", firstUser.getEmail());
                return;
            }
            String accessToken = accessTokenOpt.get();
            log.info("✓ Access Token получен, будет использован для всех {} запросов к ALOR API", positionsByTicker.size());

            // Получаем цены из ALOR API для каждого уникального тикера (кэширование)
            Map<String, BigDecimal> pricesByTicker = new HashMap<>();
            int apiRequests = 0;
            int apiErrors = 0;

            for (Map.Entry<String, List<PortfolioPosition>> entry : positionsByTicker.entrySet()) {
                String tickerKey = entry.getKey();
                String[] parts = tickerKey.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                String exchange = parts[0];
                String symbol = parts[1];

                try {
                    Optional<BigDecimal> priceOpt = alorApiClient.getQuoteBySymbol(
                            accessToken, environment, exchange, symbol);
                    
                    apiRequests++;
                    
                    if (priceOpt.isPresent()) {
                        pricesByTicker.put(tickerKey, priceOpt.get());
                    } else {
                        apiErrors++;
                        log.warn("⚠ Не удалось получить цену из ALOR для {} ({})", symbol, exchange);
                    }
                } catch (Exception e) {
                    apiErrors++;
                    log.error("Ошибка при получении цены из ALOR для {} ({}): {}", 
                            symbol, exchange, e.getMessage(), e);
                }
            }

            log.info("Выполнено {} запросов к ALOR API, получено {} цен, ошибок: {}", 
                    apiRequests, pricesByTicker.size(), apiErrors);

            // Обновляем цены во всех позициях
            int totalUpdated = 0;
            int totalSkipped = 0;

            for (Map.Entry<String, List<PortfolioPosition>> entry : positionsByTicker.entrySet()) {
                String tickerKey = entry.getKey();
                List<PortfolioPosition> positions = entry.getValue();
                BigDecimal price = pricesByTicker.get(tickerKey);

                if (price == null) {
                    totalSkipped += positions.size();
                    continue;
                }

                String[] parts = tickerKey.split(":", 2);
                String symbol = parts.length == 2 ? parts[1] : tickerKey;

                for (PortfolioPosition position : positions) {
                    BigDecimal oldPrice = position.getLastKnownPrice();
                    
                    // Для облигаций цена из ALOR приходит в процентах от номинала
                    // Конвертируем в абсолютную цену: цена_процент * номинал / 100
                    BigDecimal finalPrice = price;
                    if ("BOND".equals(position.getSecurityType())) {
                        BigDecimal nominal = getBondNominal(position);
                        if (nominal != null && nominal.compareTo(BigDecimal.ZERO) > 0) {
                            // Конвертируем процент в абсолютную цену
                            finalPrice = price.multiply(nominal)
                                    .divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
                            log.debug("Конвертирована цена облигации ISIN {} из ALOR: {}% от номинала {} = {} ₽", 
                                    position.getIsin(), price, nominal, finalPrice);
                        } else {
                            log.warn("⚠ Не удалось найти номинал для облигации ISIN {}, используем цену как есть: {} ₽", 
                                    position.getIsin(), price);
                        }
                    }
                    
                    // Обновляем last_known_price, если цена изменилась
                    if (oldPrice == null || !oldPrice.equals(finalPrice)) {
                        position.setLastKnownPrice(finalPrice);
                        positionRepository.save(position);
                        totalUpdated++;
                        
                        String shortName = position.getSecurityName();
                        if (shortName == null || shortName.isEmpty()) {
                            shortName = symbol;
                        }

                        if (oldPrice == null) {
                            if ("BOND".equals(position.getSecurityType())) {
                                log.info("✓ Обновлена цена для облигации: {} (ISIN: {}, тикер: {}) в портфеле {}: {}% от номинала = {} ₽", 
                                        shortName, position.getIsin(), symbol, position.getPortfolio().getId(), price, finalPrice);
                            } else {
                                log.info("✓ Обновлена цена для бумаги: {} (ISIN: {}, тикер: {}) в портфеле {}: установлена цена {} ₽", 
                                        shortName, position.getIsin(), symbol, position.getPortfolio().getId(), finalPrice);
                            }
                        } else {
                            BigDecimal priceChange = finalPrice.subtract(oldPrice);
                            String changeSign = priceChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                            if ("BOND".equals(position.getSecurityType())) {
                                log.info("✓ Обновлена цена для облигации: {} (ISIN: {}, тикер: {}) в портфеле {}: {}% от номинала = {} ₽ (было {} ₽, изменение: {}{} ₽)", 
                                        shortName, position.getIsin(), symbol, position.getPortfolio().getId(), 
                                        price, finalPrice, oldPrice, changeSign, priceChange);
                            } else {
                                log.info("✓ Обновлена цена для бумаги: {} (ISIN: {}, тикер: {}) в портфеле {}: {} ₽ -> {} ₽ (изменение: {}{} ₽)", 
                                        shortName, position.getIsin(), symbol, position.getPortfolio().getId(), 
                                        oldPrice, finalPrice, changeSign, priceChange);
                            }
                        }
                    }
                }
            }

            if (totalUpdated > 0) {
                log.info("═══════════════════════════════════════════════════════════");
                log.info("Обновление цен из ALOR завершено: обновлено позиций {}, пропущено {}, запросов к API: {}", 
                        totalUpdated, totalSkipped, apiRequests);
                log.info("═══════════════════════════════════════════════════════════");
            } else {
                log.info("Обновление цен из ALOR завершено: обновлено позиций {}, пропущено {}", 
                        totalUpdated, totalSkipped);
            }

        } catch (Exception e) {
            log.error("Критическая ошибка при обновлении цен из ALOR", e);
        }
    }

    /**
     * Определяет тикер для позиции через MOEX справочники
     * @param position Позиция портфеля
     * @return Тикер (secid) или null, если не удалось определить
     */
    private String getTickerFromPosition(PortfolioPosition position) {
        String isin = position.getIsin();
        if (isin == null || isin.isEmpty()) {
            return null;
        }

        // Пробуем получить тикер через связи
        if (position.getMoexStock() != null) {
            String secid = position.getMoexStock().getSecid();
            if (secid != null && !secid.isEmpty()) {
                return secid;
            }
        }

        if (position.getMoexBond() != null) {
            String secid = position.getMoexBond().getSecid();
            if (secid != null && !secid.isEmpty()) {
                return secid;
            }
        }

        // Если связи не установлены, ищем по ISIN в справочниках
        if ("STOCK".equals(position.getSecurityType())) {
            return stockRepository.findByIsin(isin)
                    .map(stock -> stock.getSecid())
                    .orElse(null);
        } else if ("BOND".equals(position.getSecurityType())) {
            return bondRepository.findByIsin(isin)
                    .map(bond -> bond.getSecid())
                    .orElse(null);
        }

        return null;
    }

    /**
     * Получает номинал облигации из справочника MOEX
     * @param position Позиция портфеля (облигация)
     * @return Номинал облигации или null, если не найден
     */
    private BigDecimal getBondNominal(PortfolioPosition position) {
        // Пробуем получить номинал через связь
        if (position.getMoexBond() != null) {
            BigDecimal facevalue = position.getMoexBond().getFacevalue();
            if (facevalue != null && facevalue.compareTo(BigDecimal.ZERO) > 0) {
                return facevalue;
            }
        }

        // Если связь не установлена, ищем по ISIN в справочнике
        String isin = position.getIsin();
        if (isin != null && !isin.isEmpty()) {
            return bondRepository.findByIsin(isin)
                    .map(bond -> bond.getFacevalue())
                    .orElse(null);
        }

        return null;
    }
}

