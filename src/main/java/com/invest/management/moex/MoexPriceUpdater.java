package com.invest.management.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import com.invest.management.moex.bond.Bond;
import com.invest.management.moex.bond.BondRepository;

@Service
@ConditionalOnProperty(name = "moex.price-updater.enabled", havingValue = "true", matchIfMissing = true)
public class MoexPriceUpdater {

    private static final Logger log = LoggerFactory.getLogger(MoexPriceUpdater.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_000;
    private static final String DEFAULT_BOARD = "TQBR";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MoexStockRepository stockRepository;
    private final MoexDataLoader dataLoader;
    private final BondRepository bondRepository;
    private final com.invest.management.moex.bond.BondDataLoader bondDataLoader;

    @Value("${moex.api.base-url:https://iss.moex.com/iss}")
    private String baseUrl;

    @Value("${moex.full-update-interval-hours:24}")
    private long fullUpdateIntervalHours;

    public MoexPriceUpdater(RestTemplate moexRestTemplate,
                            ObjectMapper objectMapper,
                            MoexStockRepository stockRepository,
                            MoexDataLoader dataLoader,
                            BondRepository bondRepository,
                            com.invest.management.moex.bond.BondDataLoader bondDataLoader) {
        this.restTemplate = moexRestTemplate;
        this.objectMapper = objectMapper;
        this.stockRepository = stockRepository;
        this.dataLoader = dataLoader;
        this.bondRepository = bondRepository;
        this.bondDataLoader = bondDataLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Проверка необходимости полного обновления данных MOEX при старте приложения");
        checkAndPerformFullUpdate();
    }

    @Scheduled(cron = "${moex.price-updater.cron:0 */5 * * * *}")
    @Transactional
    public void updateMarketPrices() {
        log.info("Начало обновления рыночных цен акций и облигаций");
        try {
            // Проверяем, нужно ли полное обновление перед обновлением цен
            if (needsFullUpdate()) {
                log.info("Обнаружена необходимость полного обновления данных. Выполняем полное обновление...");
                performFullUpdate();
            }
            updatePricesForBoard(DEFAULT_BOARD);
            updateBondPrices();
            log.info("Обновление рыночных цен завершено успешно");
        } catch (Exception ex) {
            log.error("Ошибка при обновлении рыночных цен", ex);
        }
    }

    private void checkAndPerformFullUpdate() {
        if (needsFullUpdate()) {
            log.info("Требуется полное обновление данных MOEX. Выполняем...");
            performFullUpdate();
        } else {
            log.info("Полное обновление не требуется. Последнее обновление было менее {} часов назад", 
                fullUpdateIntervalHours);
        }
    }

    private boolean needsFullUpdate() {
        long stockCount = stockRepository.countAll();
        
        // Если таблица пуста, требуется полное обновление
        if (stockCount == 0) {
            log.info("Таблица moex_stocks пуста. Требуется полное обновление.");
            return true;
        }
        
        // Проверяем, есть ли акции с listlevel=2 (после изменения фильтра они должны быть загружены)
        long listLevel2Count = stockRepository.countByListLevel2();
        if (listLevel2Count == 0 && stockCount > 0) {
            log.info("В таблице нет акций с listlevel=2. Требуется полное обновление для загрузки акций второго уровня листинга.");
            return true;
        }
        
        // Проверяем время последнего обновления
        Optional<OffsetDateTime> minUpdatedAt = stockRepository.findMinUpdatedAt();
        if (minUpdatedAt.isEmpty()) {
            log.info("Не удалось определить время последнего обновления. Требуется полное обновление.");
            return true;
        }
        
        OffsetDateTime lastUpdate = minUpdatedAt.get();
        OffsetDateTime now = OffsetDateTime.now();
        Duration duration = Duration.between(lastUpdate, now);
        long hoursSinceUpdate = duration.toHours();
        
        if (hoursSinceUpdate >= fullUpdateIntervalHours) {
            log.info("Последнее полное обновление было {} часов назад. Требуется новое обновление.", 
                hoursSinceUpdate);
            return true;
        }
        
        return false;
    }

    private void performFullUpdate() {
        try {
            log.info("Начало полного обновления всех полей таблицы moex_stocks");
            dataLoader.loadStocksListA();
            log.info("Полное обновление всех полей завершено успешно");
        } catch (Exception ex) {
            log.error("Ошибка при полном обновлении данных MOEX", ex);
        }
    }

    private void updatePricesForBoard(String boardId) {
        String path = String.format("/engines/stock/markets/shares/boards/%s/securities.json?iss.meta=off", boardId);
        JsonNode root = fetch(path);
        if (root == null) {
            log.warn("Не удалось получить данные MOEX по board {}: пустой ответ", boardId);
            return;
        }

        JsonNode marketDataNode = root.get("marketdata");
        if (marketDataNode == null || marketDataNode.isMissingNode()) {
            log.warn("Ответ MOEX не содержит секции 'marketdata' для board {}", boardId);
            return;
        }

        // Получаем также секцию securities для обновления prevprice
        JsonNode securitiesNode = root.get("securities");
        Map<String, JsonNode> securitiesBySecid = new HashMap<>();
        Map<String, Integer> secIndex = null;
        
        if (securitiesNode != null && !securitiesNode.isMissingNode()) {
            List<String> secColumns = readColumns(securitiesNode);
            secIndex = indexMap(secColumns);
            
            for (JsonNode row : securitiesNode.withArray("data")) {
                String secid = readText(row, secIndex, "secid");
                if (secid != null && !secid.isBlank()) {
                    securitiesBySecid.put(secid, row);
                }
            }
        }

        List<String> marketColumns = readColumns(marketDataNode);
        Map<String, Integer> marketIndex = indexMap(marketColumns);

        Map<String, MoexStock> stocksBySecid = new HashMap<>();
        stockRepository.findAll().forEach(stock -> {
            if (stock.getSecid() != null) {
                stocksBySecid.put(stock.getSecid(), stock);
            }
        });

        int updatedCount = 0;
        int prevpriceUpdatedCount = 0;
        
        for (JsonNode row : marketDataNode.withArray("data")) {
            String secid = readText(row, marketIndex, "secid");
            if (secid == null || secid.isBlank()) {
                continue;
            }

            MoexStock stock = stocksBySecid.get(secid);
            if (stock == null) {
                continue;
            }

            boolean stockChanged = false;

            // Обновляем marketprice
            // Используем LAST (текущая цена сделки) в первую очередь, MARKETPRICE как fallback
            BigDecimal lastPrice = readDecimal(row, marketIndex, "last");
            BigDecimal marketPrice = readDecimal(row, marketIndex, "marketprice");
            BigDecimal newPrice = lastPrice != null ? lastPrice : marketPrice;
            
            // Логируем, если цены различаются
            if (lastPrice != null && marketPrice != null && !lastPrice.equals(marketPrice)) {
                log.debug("Secid {}: LAST={}, MARKETPRICE={}, используем LAST", secid, lastPrice, marketPrice);
            }
            
            if (newPrice != null && !newPrice.equals(stock.getMarketprice())) {
                log.debug("Обновление цены для {}: {} -> {} (источник: {})", 
                    secid, stock.getMarketprice(), newPrice, 
                    lastPrice != null ? "LAST" : "MARKETPRICE");
                stock.setMarketprice(newPrice);
                stockChanged = true;
            }

            // Обновляем prevprice из секции securities
            if (secIndex != null) {
                JsonNode secRow = securitiesBySecid.get(secid);
                if (secRow != null) {
                    BigDecimal newPrevprice = readDecimal(secRow, secIndex, "prevprice");
                    if (newPrevprice != null && !newPrevprice.equals(stock.getPrevprice())) {
                        log.debug("Обновление prevprice для {}: {} -> {}", 
                            secid, stock.getPrevprice(), newPrevprice);
                        stock.setPrevprice(newPrevprice);
                        stockChanged = true;
                        prevpriceUpdatedCount++;
                    }
                }
            }

            if (stockChanged) {
                stock.setUpdatedAt(OffsetDateTime.now());
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            stockRepository.saveAll(stocksBySecid.values());
            log.info("Обновлено {} цен для board {} (в т.ч. {} prevprice)", 
                updatedCount, boardId, prevpriceUpdatedCount);
        } else {
            log.debug("Нет изменений в ценах для board {}", boardId);
        }
    }

    private void updateBondPrices() {
        String[] bondBoards = {"TQOB", "TQCB"};
        int totalUpdated = 0;
        int totalPrevpriceUpdated = 0;
        
        for (String boardId : bondBoards) {
            String path = String.format("/engines/stock/markets/bonds/boards/%s/securities.json?iss.meta=off", boardId);
            JsonNode root = fetch(path);
            if (root == null) {
                log.warn("Не удалось получить данные MOEX по board {}: пустой ответ", boardId);
                continue;
            }

            JsonNode marketDataNode = root.get("marketdata");
            if (marketDataNode == null || marketDataNode.isMissingNode()) {
                log.warn("Ответ MOEX не содержит секции 'marketdata' для board {}", boardId);
                continue;
            }

            // Получаем также секцию securities для обновления prevprice
            JsonNode securitiesNode = root.get("securities");
            Map<String, JsonNode> securitiesBySecid = new HashMap<>();
            Map<String, Integer> secIndex = null;
            
            if (securitiesNode != null && !securitiesNode.isMissingNode()) {
                List<String> secColumns = readColumns(securitiesNode);
                secIndex = indexMap(secColumns);
                
                for (JsonNode row : securitiesNode.withArray("data")) {
                    String secid = readText(row, secIndex, "secid");
                    if (secid != null && !secid.isBlank()) {
                        securitiesBySecid.put(secid, row);
                    }
                }
            }

            List<String> marketColumns = readColumns(marketDataNode);
            Map<String, Integer> marketIndex = indexMap(marketColumns);

            Map<String, Bond> bondsBySecid = new HashMap<>();
            bondRepository.findAll().forEach(bond -> {
                if (bond.getSecid() != null) {
                    bondsBySecid.put(bond.getSecid(), bond);
                }
            });

            int updatedCount = 0;
            int prevpriceUpdatedCount = 0;
            
            for (JsonNode row : marketDataNode.withArray("data")) {
                String secid = readText(row, marketIndex, "secid");
                if (secid == null || secid.isBlank()) {
                    continue;
                }

                Bond bond = bondsBySecid.get(secid);
                if (bond == null) {
                    continue;
                }

                boolean bondChanged = false;

                // Обновляем marketprice
                BigDecimal lastPrice = readDecimal(row, marketIndex, "last");
                BigDecimal marketPrice = readDecimal(row, marketIndex, "marketprice");
                BigDecimal newPrice = lastPrice != null ? lastPrice : marketPrice;

                if (newPrice != null && !newPrice.equals(bond.getMarketprice())) {
                    bond.setMarketprice(newPrice);
                    bondChanged = true;
                }

                // Обновляем prevprice из секции securities
                if (secIndex != null) {
                    JsonNode secRow = securitiesBySecid.get(secid);
                    if (secRow != null) {
                        BigDecimal newPrevprice = readDecimal(secRow, secIndex, "prevprice");
                        if (newPrevprice != null && !newPrevprice.equals(bond.getPrevprice())) {
                            log.debug("Обновление prevprice для облигации {}: {} -> {}", 
                                secid, bond.getPrevprice(), newPrevprice);
                            bond.setPrevprice(newPrevprice);
                            bondChanged = true;
                            prevpriceUpdatedCount++;
                        }
                    }
                }

                if (bondChanged) {
                    bond.setUpdatedAt(OffsetDateTime.now());
                    updatedCount++;
                }
            }

            if (updatedCount > 0) {
                bondRepository.saveAll(bondsBySecid.values());
                log.info("Обновлено {} цен облигаций для board {} (в т.ч. {} prevprice)", 
                    updatedCount, boardId, prevpriceUpdatedCount);
                totalUpdated += updatedCount;
                totalPrevpriceUpdated += prevpriceUpdatedCount;
            } else {
                log.debug("Нет изменений в ценах облигаций для board {}", boardId);
            }
        }
        
        if (totalUpdated > 0) {
            log.info("Всего обновлено {} цен облигаций (в т.ч. {} prevprice)", 
                totalUpdated, totalPrevpriceUpdated);
        }
    }

    private JsonNode fetch(String path) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Accept-Encoding", "gzip, deflate");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("Попытка {}: статус {} при запросе {}", attempt, response.getStatusCode(), url);
                } else {
                    byte[] decodedBody = decodeBody(response);
                    if (decodedBody != null) {
                        String body = new String(decodedBody, resolveCharset(response.getHeaders().getContentType()));
                        if (body.trim().startsWith("<")) {
                            log.warn("Попытка {}: ответ {} выглядит как HTML ({} символов)", attempt, url, body.length());
                        } else {
                            JsonNode node = objectMapper.readTree(body);
                            throttleBetweenCalls();
                            return node;
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Попытка {} запросить {} завершилась ошибкой: {}", attempt, url, ex.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }
        return null;
    }

    private Charset resolveCharset(MediaType mediaType) {
        if (mediaType != null && mediaType.getCharset() != null) {
            return mediaType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }

    private byte[] decodeBody(ResponseEntity<byte[]> response) throws IOException {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null) {
            if (encoding.equalsIgnoreCase("gzip")) {
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    return gis.readAllBytes();
                }
            }
            if (encoding.equalsIgnoreCase("deflate")) {
                try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(body))) {
                    return iis.readAllBytes();
                }
            }
        }
        return body;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> readColumns(JsonNode tableNode) {
        if (tableNode == null || tableNode.isMissingNode()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(
                tableNode.get("columns"),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                });
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private Map<String, Integer> indexMap(List<String> columns) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            map.put(columns.get(i).toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    private String readText(JsonNode row, Map<String, Integer> indexMap, String column) {
        if (row == null || row.isNull() || indexMap.isEmpty()) {
            return null;
        }
        Integer idx = indexMap.get(column.toLowerCase(Locale.ROOT));
        if (idx == null || idx >= row.size()) {
            return null;
        }
        JsonNode value = row.get(idx);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal readDecimal(JsonNode row, Map<String, Integer> indexMap, String primaryColumn, String... fallbacks) {
        String text = readText(row, indexMap, primaryColumn);
        if ((text == null || text.isBlank()) && fallbacks.length > 0) {
            for (String fallback : fallbacks) {
                text = readText(row, indexMap, fallback);
                if (text != null && !text.isBlank()) {
                    break;
                }
            }
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void throttleBetweenCalls() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

