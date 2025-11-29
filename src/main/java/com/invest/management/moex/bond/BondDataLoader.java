package com.invest.management.moex.bond;

import com.fasterxml.jackson.databind.JsonNode;
import com.invest.management.moex.common.MoexApiClient;
import com.invest.management.moex.common.MoexResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BondDataLoader {

    private static final Logger log = LoggerFactory.getLogger(BondDataLoader.class);
    // Основные торговые площадки для облигаций
    // TQOB - государственные облигации (ОФЗ)
    // TQCB - корпоративные облигации (БалтЛизП16, ВЭБР-40 и др.)
    private static final String[] BOND_BOARDS = {"TQOB", "TQCB"};

    private final MoexApiClient apiClient;
    private final MoexResponseParser parser;
    private final BondRepository bondRepository;

    public BondDataLoader(MoexApiClient apiClient,
                          MoexResponseParser parser,
                          BondRepository bondRepository) {
        this.apiClient = apiClient;
        this.parser = parser;
        this.bondRepository = bondRepository;
    }

    @Transactional
    public void loadBonds() {
        for (String boardId : BOND_BOARDS) {
            log.info("Загрузка облигаций с площадки {}", boardId);
            loadBondsForBoard(boardId);
        }
        log.info("Загрузка облигаций завершена");
    }

    private void loadBondsForBoard(String boardId) {
        String path = String.format("/engines/stock/markets/bonds/boards/%s/securities.json?iss.meta=off", boardId);
        JsonNode root = apiClient.fetch(path);
        if (root == null) {
            log.warn("Не удалось получить данные MOEX по board {}: пустой ответ", boardId);
            return;
        }

        JsonNode securitiesNode = root.get("securities");
        if (securitiesNode == null || securitiesNode.isMissingNode()) {
            log.warn("Ответ MOEX не содержит секции 'securities' для board {}", boardId);
            return;
        }

        JsonNode marketDataNode = root.get("marketdata");

        List<String> secColumns = parser.readColumns(securitiesNode);
        Map<String, Integer> secIndex = parser.indexMap(secColumns);

        Map<String, JsonNode> marketDataBySecId = parser.buildMarketDataIndex(marketDataNode);
        Map<String, Integer> marketIndex = marketDataNode != null ? parser.indexMap(parser.readColumns(marketDataNode)) : Map.of();

        List<Bond> bondsToPersist = new ArrayList<>();

        for (JsonNode row : securitiesNode.withArray("data")) {
            String secid = parser.readText(row, secIndex, "secid");
            if (secid == null || secid.isBlank()) {
                continue;
            }

            Bond bond = bondRepository.findBySecid(secid)
                .orElseGet(Bond::new);

            // Базовые поля
            bond.setSecid(secid);
            bond.setBoardid(parser.readText(row, secIndex, "boardid"));
            bond.setShortname(parser.readText(row, secIndex, "shortname"));
            bond.setSecname(parser.readText(row, secIndex, "secname"));
            bond.setIsin(parser.readText(row, secIndex, "isin"));
            bond.setRegnumber(parser.readText(row, secIndex, "regnumber"));
            bond.setStatus(parser.readText(row, secIndex, "status"));
            bond.setLotsize(parser.readInteger(row, secIndex, "lotsize"));
            bond.setFacevalue(parser.readDecimal(row, secIndex, "facevalue"));
            bond.setDecimals(parser.readInteger(row, secIndex, "decimals"));
            bond.setPrevprice(parser.readDecimal(row, secIndex, "prevprice"));
            bond.setMarketcap(parser.readDecimal(row, secIndex, "marketcap", "marketcapitalization"));
            bond.setMarketcode(parser.readText(row, secIndex, "marketcode"));
            bond.setInstrid(parser.readText(row, secIndex, "instrid"));
            bond.setSectorid(parser.readText(row, secIndex, "sectorid"));
            bond.setGroup(parser.readText(row, secIndex, "group"));
            bond.setCurrencyid(parser.readText(row, secIndex, "currencyid", "faceunit"));
            bond.setIssuesize(parser.readDecimal(row, secIndex, "issuesize"));
            bond.setIssuer(parser.readText(row, secIndex, "issuer"));
            bond.setIssuerCountry(parser.readText(row, secIndex, "issuer_country"));
            bond.setIssuerIndustry(parser.readText(row, secIndex, "issuer_industry"));
            bond.setIssuerSector(parser.readText(row, secIndex, "issuer_sector"));

            // Специфичные поля для облигаций
            bond.setMaturitydate(parser.readDate(row, secIndex, "maturitydate"));
            bond.setCouponpercent(parser.readDecimal(row, secIndex, "couponpercent"));
            bond.setNextcoupon(parser.readDate(row, secIndex, "nextcoupon"));
            bond.setBondtype(parser.readText(row, secIndex, "bondtype"));
            bond.setOfferdate(parser.readDate(row, secIndex, "offerdate"));

            // Рыночные данные
            JsonNode marketRow = marketDataBySecId.get(secid);
            if (marketRow != null) {
                BigDecimal lastPrice = parser.readDecimal(marketRow, marketIndex, "last");
                BigDecimal marketPrice = parser.readDecimal(marketRow, marketIndex, "marketprice");
                bond.setMarketprice(lastPrice != null ? lastPrice : marketPrice);
                bond.setTradingstatus(parser.readText(marketRow, marketIndex, "tradingstatus"));
            } else {
                bond.setMarketprice(null);
                bond.setTradingstatus(null);
            }

            bond.setUpdatedAt(OffsetDateTime.now());
            bondsToPersist.add(bond);
        }

        if (bondsToPersist.isEmpty()) {
            log.warn("По board {} не найдено облигаций", boardId);
            return;
        }

        bondRepository.saveAll(bondsToPersist);
        log.info("Обновлено {} облигаций для board {}", bondsToPersist.size(), boardId);
    }

    /**
     * Загружает одну облигацию по ISIN из MOEX API (для погашенных облигаций)
     * @param isin ISIN облигации
     * @return true если облигация успешно загружена, false если не найдена
     */
    @Transactional
    public boolean loadBondByIsin(String isin) {
        log.info("=== loadBondByIsin вызван для ISIN: {} ===", isin);
        
        if (isin == null || isin.isBlank() || !isin.startsWith("RU")) {
            log.warn("ISIN {} невалиден или не начинается с RU", isin);
            return false;
        }
        
        // Проверяем, не загружена ли уже
        boolean alreadyExists = bondRepository.findByIsin(isin).isPresent();
        log.info("Проверка существования в БД для ISIN {}: {}", isin, alreadyExists ? "уже есть" : "не найдено");
        if (alreadyExists) {
            log.info("Облигация с ISIN {} уже есть в справочнике", isin);
            return true;
        }
        
        log.info("Попытка загрузить облигацию по ISIN {} из MOEX API", isin);
        
        // Пробуем найти через эндпоинт поиска по ISIN
        String path = String.format("/securities/%s.json?iss.meta=off&iss.only=securities", isin);
        log.info("Запрос к MOEX API: {}", path);
        JsonNode root = apiClient.fetch(path);
        
        if (root == null) {
            log.warn("Не удалось получить данные MOEX для ISIN {}: пустой ответ", isin);
            return false;
        }
        log.info("Получен ответ от MOEX API для ISIN {}", isin);
        
        JsonNode securitiesNode = root.get("securities");
        if (securitiesNode == null || securitiesNode.isMissingNode()) {
            log.warn("Ответ MOEX не содержит секции 'securities' для ISIN {}", isin);
            return false;
        }
        
        JsonNode dataArray = securitiesNode.get("data");
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            log.warn("Не найдено данных для ISIN {} в MOEX API (массив пуст или отсутствует)", isin);
            return false;
        }
        log.info("Найдено {} записей в ответе MOEX для ISIN {}", dataArray.size(), isin);
        
        // Парсим данные
        List<String> columns = parser.readColumns(securitiesNode);
        Map<String, Integer> index = parser.indexMap(columns);
        log.info("Колонки в ответе: {}", columns);
        
        for (JsonNode row : dataArray) {
            String foundIsin = parser.readText(row, index, "isin");
            log.info("Обработка записи с ISIN: {}", foundIsin);
            
            if (isin.equals(foundIsin)) {
                String secid = parser.readText(row, index, "secid");
                String group = parser.readText(row, index, "group");
                log.info("Найдена запись с совпадающим ISIN. secid: {}, group: {}", secid, group);
                
                // Проверяем, что это облигация
                if (secid != null && !secid.isBlank()) {
                    log.info("Пробуем загрузить через площадки облигаций для secid: {}", secid);
                    // Пробуем загрузить через площадки облигаций
                    for (String boardId : BOND_BOARDS) {
                        String boardPath = String.format("/engines/stock/markets/bonds/boards/%s/securities/%s.json?iss.meta=off", 
                            boardId, secid);
                        log.info("Запрос к площадке {}: {}", boardId, boardPath);
                        JsonNode boardRoot = apiClient.fetch(boardPath);
                        
                        if (boardRoot != null) {
                            JsonNode boardSecuritiesNode = boardRoot.get("securities");
                            if (boardSecuritiesNode != null) {
                                JsonNode boardDataArray = boardSecuritiesNode.get("data");
                                if (boardDataArray != null && boardDataArray.isArray() && !boardDataArray.isEmpty()) {
                                    log.info("Найдено на площадке {}: {} записей", boardId, boardDataArray.size());
                                    // Нашли на площадке облигаций - парсим и сохраняем
                                    boolean saved = parseAndSaveBond(boardSecuritiesNode, boardRoot.get("marketdata"), boardId);
                                    log.info("Результат сохранения с площадки {}: {}", boardId, saved);
                                    return saved;
                                } else {
                                    log.info("На площадке {} нет данных для secid {}", boardId, secid);
                                }
                            } else {
                                log.info("На площадке {} нет секции securities", boardId);
                            }
                        } else {
                            log.info("Пустой ответ от площадки {}", boardId);
                        }
                    }
                    
                    // Если не нашли на площадках, но есть в общем поиске - создаем запись из общих данных
                    // (для погашенных облигаций может не быть на активных площадках)
                    log.info("Проверка group для создания записи из общих данных. group: {}", group);
                    if (group != null && (group.toLowerCase().contains("bond") || 
                        group.toLowerCase().contains("облигация"))) {
                        log.info("Group указывает на облигацию, создаем запись из общих данных");
                        boolean saved = parseAndSaveBondFromGeneralData(row, index, secid);
                        log.info("Результат сохранения из общих данных: {}", saved);
                        return saved;
                    } else {
                        log.warn("Group '{}' не указывает на облигацию", group);
                    }
                } else {
                    log.warn("secid пустой или null");
                }
            } else {
                log.debug("ISIN не совпадает: ожидали {}, получили {}", isin, foundIsin);
            }
        }
        
        log.warn("Не удалось загрузить облигацию для ISIN {}", isin);
        return false;
    }

    /**
     * Парсит и сохраняет облигацию из данных площадки
     */
    private boolean parseAndSaveBond(JsonNode securitiesNode, JsonNode marketDataNode, String boardId) {
        List<String> secColumns = parser.readColumns(securitiesNode);
        Map<String, Integer> secIndex = parser.indexMap(secColumns);
        
        Map<String, JsonNode> marketDataBySecId = parser.buildMarketDataIndex(marketDataNode);
        Map<String, Integer> marketIndex = marketDataNode != null ? parser.indexMap(parser.readColumns(marketDataNode)) : Map.of();
        
        JsonNode dataArray = securitiesNode.get("data");
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            return false;
        }
        
        for (JsonNode row : dataArray) {
            String secid = parser.readText(row, secIndex, "secid");
            if (secid == null || secid.isBlank()) {
                continue;
            }
            
            Bond bond = bondRepository.findBySecid(secid)
                .orElseGet(Bond::new);
            
            // Заполняем все поля как в loadBondsForBoard
            bond.setSecid(secid);
            bond.setBoardid(parser.readText(row, secIndex, "boardid"));
            bond.setShortname(parser.readText(row, secIndex, "shortname"));
            bond.setSecname(parser.readText(row, secIndex, "secname"));
            bond.setIsin(parser.readText(row, secIndex, "isin"));
            bond.setRegnumber(parser.readText(row, secIndex, "regnumber"));
            bond.setStatus(parser.readText(row, secIndex, "status"));
            bond.setLotsize(parser.readInteger(row, secIndex, "lotsize"));
            bond.setFacevalue(parser.readDecimal(row, secIndex, "facevalue"));
            bond.setDecimals(parser.readInteger(row, secIndex, "decimals"));
            bond.setPrevprice(parser.readDecimal(row, secIndex, "prevprice"));
            bond.setMarketcap(parser.readDecimal(row, secIndex, "marketcap", "marketcapitalization"));
            bond.setMarketcode(parser.readText(row, secIndex, "marketcode"));
            bond.setInstrid(parser.readText(row, secIndex, "instrid"));
            bond.setSectorid(parser.readText(row, secIndex, "sectorid"));
            bond.setGroup(parser.readText(row, secIndex, "group"));
            bond.setCurrencyid(parser.readText(row, secIndex, "currencyid", "faceunit"));
            bond.setIssuesize(parser.readDecimal(row, secIndex, "issuesize"));
            bond.setIssuer(parser.readText(row, secIndex, "issuer"));
            bond.setIssuerCountry(parser.readText(row, secIndex, "issuer_country"));
            bond.setIssuerIndustry(parser.readText(row, secIndex, "issuer_industry"));
            bond.setIssuerSector(parser.readText(row, secIndex, "issuer_sector"));
            
            // Специфичные поля для облигаций
            bond.setMaturitydate(parser.readDate(row, secIndex, "maturitydate"));
            bond.setCouponpercent(parser.readDecimal(row, secIndex, "couponpercent"));
            bond.setNextcoupon(parser.readDate(row, secIndex, "nextcoupon"));
            bond.setBondtype(parser.readText(row, secIndex, "bondtype"));
            bond.setOfferdate(parser.readDate(row, secIndex, "offerdate"));
            
            // Рыночные данные
            JsonNode marketRow = marketDataBySecId.get(secid);
            if (marketRow != null) {
                BigDecimal lastPrice = parser.readDecimal(marketRow, marketIndex, "last");
                BigDecimal marketPrice = parser.readDecimal(marketRow, marketIndex, "marketprice");
                bond.setMarketprice(lastPrice != null ? lastPrice : marketPrice);
                bond.setTradingstatus(parser.readText(marketRow, marketIndex, "tradingstatus"));
            } else {
                bond.setMarketprice(null);
                bond.setTradingstatus(null);
            }
            
            bond.setUpdatedAt(OffsetDateTime.now());
            Bond savedBond = bondRepository.save(bond);
            log.info("Облигация с ISIN {} успешно загружена из MOEX API. Сохранена с ID: {}", bond.getIsin(), savedBond.getId());
            
            // Проверяем, что действительно сохранилось
            boolean existsAfterSave = bondRepository.findByIsin(bond.getIsin()).isPresent();
            log.info("Проверка после сохранения для ISIN {}: {}", bond.getIsin(), existsAfterSave ? "НАЙДЕНО" : "НЕ НАЙДЕНО");
            
            return true;
        }
        
        return false;
    }

    /**
     * Парсит и сохраняет облигацию из общих данных (для погашенных облигаций)
     */
    private boolean parseAndSaveBondFromGeneralData(JsonNode row, Map<String, Integer> index, String secid) {
        Bond bond = bondRepository.findBySecid(secid)
            .orElseGet(Bond::new);
        
        bond.setSecid(secid);
        bond.setIsin(parser.readText(row, index, "isin"));
        bond.setShortname(parser.readText(row, index, "shortname"));
        bond.setSecname(parser.readText(row, index, "secname"));
        bond.setRegnumber(parser.readText(row, index, "regnumber"));
        bond.setStatus(parser.readText(row, index, "status"));
        bond.setGroup(parser.readText(row, index, "group"));
        bond.setCurrencyid(parser.readText(row, index, "currencyid", "faceunit"));
        bond.setUpdatedAt(OffsetDateTime.now());
        
        Bond savedBond = bondRepository.save(bond);
        log.info("Облигация с ISIN {} загружена из общих данных MOEX API (возможно погашена). Сохранена с ID: {}", 
            bond.getIsin(), savedBond.getId());
        
        // Проверяем, что действительно сохранилось
        boolean existsAfterSave = bondRepository.findByIsin(bond.getIsin()).isPresent();
        log.info("Проверка после сохранения из общих данных для ISIN {}: {}", 
            bond.getIsin(), existsAfterSave ? "НАЙДЕНО" : "НЕ НАЙДЕНО");
        
        return true;
    }
}

