package com.invest.management.alor.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invest.management.alor.dto.AlorCashMovement;
import com.invest.management.alor.dto.AlorOrderbook;
import com.invest.management.alor.dto.AlorPosition;
import com.invest.management.alor.dto.AlorTransaction;
import com.invest.management.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Клиент для работы с ALOR OpenAPI
 */
@Component
public class AlorApiClient {

    private static final Logger log = LoggerFactory.getLogger(AlorApiClient.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlorTokenService tokenService;
    
    @Value("${alor.api.test-base-url:https://apidev.alor.ru}")
    private String testBaseUrl;
    
    @Value("${alor.api.production-base-url:https://api.alor.ru}")
    private String productionBaseUrl;

    public AlorApiClient(RestTemplate alorRestTemplate,
                        ObjectMapper objectMapper,
                        AlorTokenService tokenService) {
        this.restTemplate = alorRestTemplate;
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
    }

    /**
     * Получает Access Token для пользователя
     */
    private Optional<String> getAccessToken(AppUser user, String environment) {
        Optional<AlorTokenService.AccessTokenResponse> tokenResponse = 
                tokenService.getAccessToken(user, environment);
        return tokenResponse.map(AlorTokenService.AccessTokenResponse::getAccessToken);
    }

    /**
     * Получает Access Token для пользователя (публичный метод для кэширования)
     */
    public Optional<String> getAccessTokenForUser(AppUser user, String environment) {
        Optional<AlorTokenService.AccessTokenResponse> tokenResponse = 
                tokenService.getAccessToken(user, environment);
        return tokenResponse.map(AlorTokenService.AccessTokenResponse::getAccessToken);
    }

    /**
     * Создает заголовки с авторизацией
     */
    private HttpHeaders createAuthHeaders(AppUser user, String environment) {
        HttpHeaders headers = new HttpHeaders();
        Optional<String> accessToken = getAccessToken(user, environment);
        if (accessToken.isEmpty()) {
            throw new RuntimeException("Не удалось получить Access Token для пользователя " + user.getEmail());
        }
        headers.set("Authorization", "Bearer " + accessToken.get());
        return headers;
    }

    /**
     * Получает сделки за период
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/trades
     */
    public List<AlorTransaction> getTransactions(AppUser user, String environment, 
                                                  String portfolioId, 
                                                  LocalDate startDate, 
                                                  LocalDate endDate,
                                                  String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        String url = String.format("%s/md/v2/Clients/%s/%s/trades?from=%s&to=%s", 
                baseUrl, exchange, portfolioId, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                // TODO: Адаптировать парсинг под реальную структуру ответа ALOR API
                return objectMapper.convertValue(json, new TypeReference<List<AlorTransaction>>() {});
            }
            
            log.warn("Не удалось получить сделки: статус {}", response.getStatusCode());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении сделок для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получает текущие позиции портфеля
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/positions
     */
    public List<AlorPosition> getPositions(AppUser user, String environment, String portfolioId, String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        String url = String.format("%s/md/v2/Clients/%s/%s/positions", 
                baseUrl, exchange, portfolioId);
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                // TODO: Адаптировать парсинг под реальную структуру ответа ALOR API
                return objectMapper.convertValue(json, new TypeReference<List<AlorPosition>>() {});
            }
            
            log.warn("Не удалось получить позиции: статус {}", response.getStatusCode());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении позиций для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получает движения денежных средств за период
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/money
     */
    public List<AlorCashMovement> getCashMovements(AppUser user, String environment,
                                                   String portfolioId,
                                                   LocalDate startDate,
                                                   LocalDate endDate,
                                                   String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        String url = String.format("%s/md/v2/Clients/%s/%s/money?from=%s&to=%s", 
                baseUrl, exchange, portfolioId, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                // TODO: Адаптировать парсинг под реальную структуру ответа ALOR API
                return objectMapper.convertValue(json, new TypeReference<List<AlorCashMovement>>() {});
            }
            
            log.warn("Не удалось получить движения денежных средств: статус {}", response.getStatusCode());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при получении движений денежных средств для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Получает сырые данные позиций (для тестирования)
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/positions
     */
    public String getPositionsRaw(AppUser user, String environment, String portfolioId, String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        // Правильный endpoint согласно документации ALOR
        String url = String.format("%s/md/v2/Clients/%s/%s/positions", 
                baseUrl, exchange, portfolioId);
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Запрос позиций ALOR: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody() != null ? response.getBody() : "Пустой ответ";
            }
            
            String errorBody = response.getBody() != null ? response.getBody() : "Нет тела ответа";
            log.error("Ошибка при получении позиций ALOR: статус {}, тело: {}", response.getStatusCode(), errorBody);
            return String.format("Ошибка: %s\nТело ответа: %s", response.getStatusCode(), errorBody);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Нет тела ответа";
            log.error("HTTP ошибка при получении позиций ALOR: статус {}, тело: {}", e.getStatusCode(), errorBody, e);
            return String.format("HTTP ошибка: %s\nТело ответа: %s", e.getStatusCode(), errorBody);
        } catch (Exception e) {
            log.error("Ошибка при получении сырых данных позиций: {}", e.getMessage(), e);
            return "Ошибка: " + e.getMessage();
        }
    }

    /**
     * Получает сырые данные сделок (для тестирования)
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/trades
     */
    public String getTransactionsRaw(AppUser user, String environment, String portfolioId, 
                                     LocalDate from, LocalDate to, String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        // Правильный endpoint согласно документации ALOR
        String url = String.format("%s/md/v2/Clients/%s/%s/trades?from=%s&to=%s", 
                baseUrl, exchange, portfolioId, from.format(DATE_FORMATTER), to.format(DATE_FORMATTER));
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Запрос сделок ALOR: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody() != null ? response.getBody() : "Пустой ответ";
            }
            
            String errorBody = response.getBody() != null ? response.getBody() : "Нет тела ответа";
            log.error("Ошибка при получении сделок ALOR: статус {}, тело: {}", response.getStatusCode(), errorBody);
            return String.format("Ошибка: %s\nТело ответа: %s", response.getStatusCode(), errorBody);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Нет тела ответа";
            log.error("HTTP ошибка при получении сделок ALOR: статус {}, тело: {}", e.getStatusCode(), errorBody, e);
            return String.format("HTTP ошибка: %s\nТело ответа: %s", e.getStatusCode(), errorBody);
        } catch (Exception e) {
            log.error("Ошибка при получении сырых данных сделок: {}", e.getMessage(), e);
            return "Ошибка: " + e.getMessage();
        }
    }

    /**
     * Получает сырые данные движений денежных средств (для тестирования)
     * Согласно документации ALOR API: GET /md/v2/Clients/{exchange}/{portfolio}/money
     */
    public String getCashMovementsRaw(AppUser user, String environment, String portfolioId,
                                      LocalDate from, LocalDate to, String exchange) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        // Правильный endpoint согласно документации ALOR
        String url = String.format("%s/md/v2/Clients/%s/%s/money?from=%s&to=%s", 
                baseUrl, exchange, portfolioId, from.format(DATE_FORMATTER), to.format(DATE_FORMATTER));
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Запрос движений денежных средств ALOR: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody() != null ? response.getBody() : "Пустой ответ";
            }
            
            String errorBody = response.getBody() != null ? response.getBody() : "Нет тела ответа";
            log.error("Ошибка при получении движений денежных средств ALOR: статус {}, тело: {}", response.getStatusCode(), errorBody);
            return String.format("Ошибка: %s\nТело ответа: %s", response.getStatusCode(), errorBody);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Нет тела ответа";
            log.error("HTTP ошибка при получении движений денежных средств ALOR: статус {}, тело: {}", e.getStatusCode(), errorBody, e);
            return String.format("HTTP ошибка: %s\nТело ответа: %s", e.getStatusCode(), errorBody);
        } catch (Exception e) {
            log.error("Ошибка при получении сырых данных движений денежных средств: {}", e.getMessage(), e);
            return "Ошибка: " + e.getMessage();
        }
    }

    /**
     * Получает котировку (биржавой стакан) по тикеру
     * Согласно документации ALOR API: GET /md/v2/orderbooks/{exchange}/{symbol}
     * 
     * @param user Пользователь для авторизации
     * @param environment Окружение (test/production)
     * @param exchange Биржа (MOEX, SPBX и т.д.)
     * @param symbol Тикер инструмента (например, SBER, GAZP)
     * @return Optional с ценой или пустое, если не удалось получить
     */
    public Optional<BigDecimal> getQuoteBySymbol(AppUser user, String environment, 
                                                  String exchange, String symbol) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        // Если exchange не указан, пробуем MOEX по умолчанию
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        String url = String.format("%s/md/v2/orderbooks/%s/%s", 
                baseUrl, exchange, symbol);
        
        try {
            HttpHeaders headers = createAuthHeaders(user, environment);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                AlorOrderbook orderbook = objectMapper.convertValue(json, AlorOrderbook.class);
                
                BigDecimal price = orderbook.getCurrentPrice();
                if (price != null) {
                    log.debug("Получена котировка для {} ({}): {} ₽", symbol, exchange, price);
                    return Optional.of(price);
                } else {
                    log.warn("Котировка для {} ({}) не содержит цены", symbol, exchange);
                    return Optional.empty();
                }
            }
            
            log.warn("Не удалось получить котировку для {} ({}): статус {}", 
                    symbol, exchange, response.getStatusCode());
            return Optional.empty();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Нет тела ответа";
            log.warn("HTTP ошибка при получении котировки для {} ({}): статус {}, тело: {}", 
                    symbol, exchange, e.getStatusCode(), errorBody);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при получении котировки для {} ({}): {}", 
                    symbol, exchange, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Получает котировку по тикеру используя готовый Access Token
     * Используется для оптимизации - позволяет кэшировать токен для множественных запросов
     */
    public Optional<BigDecimal> getQuoteBySymbol(String accessToken, String environment, 
                                                  String exchange, String symbol) {
        String baseUrl = "test".equals(environment) ? testBaseUrl : productionBaseUrl;
        if (exchange == null || exchange.isEmpty()) {
            exchange = "MOEX";
        }
        String url = String.format("%s/md/v2/orderbooks/%s/%s", 
                baseUrl, exchange, symbol);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            log.info("Запрос цены из ALOR для тикера {} (биржа: {}): {}", symbol, exchange, url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                AlorOrderbook orderbook = objectMapper.convertValue(json, AlorOrderbook.class);
                
                BigDecimal price = orderbook.getCurrentPrice();
                if (price != null) {
                    log.info("✓ Получена цена из ALOR для {} ({}): {} ₽", symbol, exchange, price);
                    return Optional.of(price);
                } else {
                    log.warn("Котировка для {} ({}) не содержит цены", symbol, exchange);
                    return Optional.empty();
                }
            }
            
            log.warn("Не удалось получить котировку для {} ({}): статус {}", 
                    symbol, exchange, response.getStatusCode());
            return Optional.empty();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : "Нет тела ответа";
            log.warn("HTTP ошибка при получении котировки для {} ({}): статус {}, тело: {}", 
                    symbol, exchange, e.getStatusCode(), errorBody);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при получении котировки для {} ({}): {}", 
                    symbol, exchange, e.getMessage(), e);
            return Optional.empty();
        }
    }
}

