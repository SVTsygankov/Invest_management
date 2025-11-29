package com.invest.management.moex.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MoexResponseParser {

    private final ObjectMapper objectMapper;

    public MoexResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> readColumns(JsonNode tableNode) {
        if (tableNode == null || tableNode.isMissingNode()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(
                tableNode.get("columns"),
                new TypeReference<List<String>>() {
                });
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    public Map<String, Integer> indexMap(List<String> columns) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            map.put(columns.get(i).toLowerCase(Locale.ROOT), i);
        }
        return map;
    }

    public String readText(JsonNode row, Map<String, Integer> indexMap, String column) {
        return readText(row, indexMap, column, new String[0]);
    }

    public String readText(JsonNode row, Map<String, Integer> indexMap, String column, String... fallbacks) {
        if (row == null || row.isNull() || indexMap.isEmpty()) {
            return null;
        }
        String value = readTextValue(row, indexMap, column);
        if ((value == null || value.isBlank()) && fallbacks != null) {
            for (String fallback : fallbacks) {
                value = readTextValue(row, indexMap, fallback);
                if (value != null && !value.isBlank()) {
                    break;
                }
            }
        }
        return value;
    }

    private String readTextValue(JsonNode row, Map<String, Integer> indexMap, String column) {
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

    public Integer readInteger(JsonNode row, Map<String, Integer> indexMap, String column) {
        String text = readText(row, indexMap, column);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public BigDecimal readDecimal(JsonNode row, Map<String, Integer> indexMap, String primaryColumn, String... fallbacks) {
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

    public Map<String, JsonNode> buildMarketDataIndex(JsonNode marketDataNode) {
        if (marketDataNode == null || marketDataNode.isMissingNode()) {
            return Map.of();
        }
        List<String> columns = readColumns(marketDataNode);
        Map<String, Integer> index = indexMap(columns);
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode row : marketDataNode.withArray("data")) {
            String secid = readText(row, index, "secid");
            if (secid != null) {
                map.put(secid, row);
            }
        }
        return map;
    }

    public LocalDate readDate(JsonNode row, Map<String, Integer> indexMap, String column) {
        String text = readText(row, indexMap, column);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            // MOEX использует формат YYYY-MM-DD
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}

