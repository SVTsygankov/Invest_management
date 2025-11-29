package com.invest.management.alor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO для ответа ALOR API orderbooks
 * GET /md/v2/orderbooks/{exchange}/{symbol}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlorOrderbook {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("bids")
    private List<OrderLevel> bids;

    @JsonProperty("asks")
    private List<OrderLevel> asks;

    @JsonProperty("lastPrice")
    private BigDecimal lastPrice;

    @JsonProperty("lastSize")
    private BigDecimal lastSize;

    @JsonProperty("timestamp")
    private Long timestamp;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public List<OrderLevel> getBids() {
        return bids;
    }

    public void setBids(List<OrderLevel> bids) {
        this.bids = bids;
    }

    public List<OrderLevel> getAsks() {
        return asks;
    }

    public void setAsks(List<OrderLevel> asks) {
        this.asks = asks;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getLastSize() {
        return lastSize;
    }

    public void setLastSize(BigDecimal lastSize) {
        this.lastSize = lastSize;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Получает текущую цену из стакана
     * Приоритет: lastPrice > средняя bid/ask > лучший bid или ask
     */
    public BigDecimal getCurrentPrice() {
        if (lastPrice != null) {
            return lastPrice;
        }

        // Если нет lastPrice, вычисляем среднюю цену из стакана
        BigDecimal bestBid = getBestBid();
        BigDecimal bestAsk = getBestAsk();

        if (bestBid != null && bestAsk != null) {
            return bestBid.add(bestAsk).divide(new BigDecimal("2"), 6, java.math.RoundingMode.HALF_UP);
        }

        if (bestBid != null) {
            return bestBid;
        }

        if (bestAsk != null) {
            return bestAsk;
        }

        return null;
    }

    private BigDecimal getBestBid() {
        if (bids != null && !bids.isEmpty()) {
            return bids.get(0).getPrice();
        }
        return null;
    }

    private BigDecimal getBestAsk() {
        if (asks != null && !asks.isEmpty()) {
            return asks.get(0).getPrice();
        }
        return null;
    }

    /**
     * Уровень в стакане заявок
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderLevel {
        @JsonProperty("price")
        private BigDecimal price;

        @JsonProperty("volume")
        private BigDecimal volume;

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public BigDecimal getVolume() {
            return volume;
        }

        public void setVolume(BigDecimal volume) {
            this.volume = volume;
        }
    }
}

