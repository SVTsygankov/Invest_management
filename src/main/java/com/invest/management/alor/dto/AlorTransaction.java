package com.invest.management.alor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для сделки из ALOR API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlorTransaction {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("orderNo")
    private Long orderNo;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("isin")
    private String isin;
    
    @JsonProperty("exchange")
    private String exchange;
    
    @JsonProperty("board")
    private String board;
    
    @JsonProperty("qty")
    private BigDecimal quantity;
    
    @JsonProperty("price")
    private BigDecimal price;
    
    @JsonProperty("value")
    private BigDecimal value;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("date")
    private LocalDateTime date;
    
    @JsonProperty("side")
    private String side; // "buy" или "sell"
    
    @JsonProperty("commission")
    private BigDecimal commission;
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getOrderNo() {
        return orderNo;
    }
    
    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }
    
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
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public BigDecimal getValue() {
        return value;
    }
    
    public void setValue(BigDecimal value) {
        this.value = value;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public LocalDateTime getDate() {
        return date;
    }
    
    public void setDate(LocalDateTime date) {
        this.date = date;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public BigDecimal getCommission() {
        return commission;
    }
    
    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }
}

