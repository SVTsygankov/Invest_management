package com.invest.management.alor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO для позиции из ALOR API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlorPosition {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("isin")
    private String isin;
    
    @JsonProperty("brokerSymbol")
    private String brokerSymbol;
    
    @JsonProperty("exchange")
    private String exchange;
    
    @JsonProperty("board")
    private String board;
    
    @JsonProperty("qty")
    private BigDecimal qty; // Количество лотов
    
    @JsonProperty("qtyUnits")
    private BigDecimal qtyUnits; // Количество в штуках
    
    @JsonProperty("qtyAvailable")
    private BigDecimal quantityAvailable;
    
    @JsonProperty("openUnits")
    private BigDecimal openUnits;
    
    @JsonProperty("lotSize")
    private Integer lotSize;
    
    @JsonProperty("avgPrice")
    private BigDecimal avgPrice;
    
    @JsonProperty("currentPrice")
    private BigDecimal currentPrice;
    
    @JsonProperty("volume")
    private BigDecimal volume; // Стоимость по средней цене
    
    @JsonProperty("currentVolume")
    private BigDecimal currentVolume; // Стоимость по текущей цене
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("shortName")
    private String shortName;
    
    @JsonProperty("securityType")
    private String securityType; // "STOCK", "BOND", etc.
    
    @JsonProperty("isCurrency")
    private Boolean isCurrency;
    
    @JsonProperty("dailyUnrealisedPl")
    private BigDecimal dailyUnrealisedPl;
    
    @JsonProperty("unrealisedPl")
    private BigDecimal unrealisedPl;
    
    @JsonProperty("portfolio")
    private String portfolio;
    
    @JsonProperty("existing")
    private Boolean existing;
    
    // Getters and setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getIsin() {
        return isin;
    }
    
    public void setIsin(String isin) {
        this.isin = isin;
    }
    
    public String getBrokerSymbol() {
        return brokerSymbol;
    }
    
    public void setBrokerSymbol(String brokerSymbol) {
        this.brokerSymbol = brokerSymbol;
    }
    
    public String getExchange() {
        return exchange;
    }
    
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }
    
    public String getBoard() {
        return board;
    }
    
    public void setBoard(String board) {
        this.board = board;
    }
    
    public BigDecimal getQty() {
        return qty;
    }
    
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }
    
    public BigDecimal getQtyUnits() {
        return qtyUnits;
    }
    
    public void setQtyUnits(BigDecimal qtyUnits) {
        this.qtyUnits = qtyUnits;
    }
    
    public BigDecimal getQuantityAvailable() {
        return quantityAvailable;
    }
    
    public void setQuantityAvailable(BigDecimal quantityAvailable) {
        this.quantityAvailable = quantityAvailable;
    }
    
    public BigDecimal getOpenUnits() {
        return openUnits;
    }
    
    public void setOpenUnits(BigDecimal openUnits) {
        this.openUnits = openUnits;
    }
    
    public Integer getLotSize() {
        return lotSize;
    }
    
    public void setLotSize(Integer lotSize) {
        this.lotSize = lotSize;
    }
    
    public BigDecimal getAvgPrice() {
        return avgPrice;
    }
    
    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }
    
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public BigDecimal getVolume() {
        return volume;
    }
    
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }
    
    public BigDecimal getCurrentVolume() {
        return currentVolume;
    }
    
    public void setCurrentVolume(BigDecimal currentVolume) {
        this.currentVolume = currentVolume;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getShortName() {
        return shortName;
    }
    
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
    
    public String getSecurityType() {
        return securityType;
    }
    
    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }
    
    public Boolean getIsCurrency() {
        return isCurrency;
    }
    
    public void setIsCurrency(Boolean isCurrency) {
        this.isCurrency = isCurrency;
    }
    
    public BigDecimal getDailyUnrealisedPl() {
        return dailyUnrealisedPl;
    }
    
    public void setDailyUnrealisedPl(BigDecimal dailyUnrealisedPl) {
        this.dailyUnrealisedPl = dailyUnrealisedPl;
    }
    
    public BigDecimal getUnrealisedPl() {
        return unrealisedPl;
    }
    
    public void setUnrealisedPl(BigDecimal unrealisedPl) {
        this.unrealisedPl = unrealisedPl;
    }
    
    public String getPortfolio() {
        return portfolio;
    }
    
    public void setPortfolio(String portfolio) {
        this.portfolio = portfolio;
    }
    
    public Boolean getExisting() {
        return existing;
    }
    
    public void setExisting(Boolean existing) {
        this.existing = existing;
    }
}

