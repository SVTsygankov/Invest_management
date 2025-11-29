package com.invest.management.moex;

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
public class MoexDataLoader {

    private static final Logger log = LoggerFactory.getLogger(MoexDataLoader.class);
    private static final String DEFAULT_BOARD = "TQBR";

    private final MoexApiClient apiClient;
    private final MoexResponseParser parser;
    private final MoexStockRepository stockRepository;

    public MoexDataLoader(MoexApiClient apiClient,
                          MoexResponseParser parser,
                          MoexStockRepository stockRepository) {
        this.apiClient = apiClient;
        this.parser = parser;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void loadStocksListA() {
        loadStocksForBoard(DEFAULT_BOARD);
    }

    private void loadStocksForBoard(String boardId) {
        String path = String.format("/engines/stock/markets/shares/boards/%s/securities.json?iss.meta=off", boardId);
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

        List<MoexStock> stocksToPersist = new ArrayList<>();

        for (JsonNode row : securitiesNode.withArray("data")) {
            String secid = parser.readText(row, secIndex, "secid");
            if (secid == null || secid.isBlank()) {
                continue;
            }

            String listLevel = parser.readText(row, secIndex, "listlevel");
            // Загружаем акции списка A: listlevel = 1 (первый уровень) и listlevel = 2 (второй уровень)
            if (listLevel != null && !listLevel.trim().matches("[12]")) {
                continue;
            }

            MoexStock stock = stockRepository.findBySecid(secid)
                .orElseGet(MoexStock::new);

            stock.setSecid(secid);
            stock.setBoardid(parser.readText(row, secIndex, "boardid"));
            stock.setShortname(parser.readText(row, secIndex, "shortname"));
            stock.setSecname(parser.readText(row, secIndex, "secname"));
            stock.setIsin(parser.readText(row, secIndex, "isin"));
            stock.setRegnumber(parser.readText(row, secIndex, "regnumber"));
            stock.setStatus(parser.readText(row, secIndex, "status"));
            stock.setLotsize(parser.readInteger(row, secIndex, "lotsize"));
            stock.setFacevalue(parser.readDecimal(row, secIndex, "facevalue"));
            stock.setDecimals(parser.readInteger(row, secIndex, "decimals"));
            stock.setPrevprice(parser.readDecimal(row, secIndex, "prevprice"));
            stock.setMarketcap(parser.readDecimal(row, secIndex, "marketcap", "marketcapitalization"));
            stock.setMarketcode(parser.readText(row, secIndex, "marketcode"));
            stock.setInstrid(parser.readText(row, secIndex, "instrid"));
            stock.setSectorid(parser.readText(row, secIndex, "sectorid"));
            stock.setGroup(parser.readText(row, secIndex, "group"));
            stock.setCurrencyid(parser.readText(row, secIndex, "currencyid", "faceunit"));
            stock.setIssuesize(parser.readDecimal(row, secIndex, "issuesize"));
            stock.setListlevel(parser.readInteger(row, secIndex, "listlevel"));
            stock.setIssuer(parser.readText(row, secIndex, "issuer"));
            stock.setIssuerCountry(parser.readText(row, secIndex, "issuer_country"));
            stock.setIssuerIndustry(parser.readText(row, secIndex, "issuer_industry"));
            stock.setIssuerSector(parser.readText(row, secIndex, "issuer_sector"));

            JsonNode marketRow = marketDataBySecId.get(secid);
            if (marketRow != null) {
                // Используем LAST (текущая цена сделки) в первую очередь, MARKETPRICE как fallback
                BigDecimal lastPrice = parser.readDecimal(marketRow, marketIndex, "last");
                BigDecimal marketPrice = parser.readDecimal(marketRow, marketIndex, "marketprice");
                stock.setMarketprice(lastPrice != null ? lastPrice : marketPrice);
                stock.setTradingstatus(parser.readText(marketRow, marketIndex, "tradingstatus"));
            } else {
                stock.setMarketprice(null);
                stock.setTradingstatus(null);
            }

            stock.setUpdatedAt(OffsetDateTime.now());
            stocksToPersist.add(stock);
        }

        if (stocksToPersist.isEmpty()) {
            log.warn("По board {} не найдено записей списка A", boardId);
            return;
        }

        stockRepository.saveAll(stocksToPersist);
        log.info("Обновлено {} акций списка A для board {}", stocksToPersist.size(), boardId);
    }
}

