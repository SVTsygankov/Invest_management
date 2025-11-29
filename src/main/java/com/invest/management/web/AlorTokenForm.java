package com.invest.management.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AlorTokenForm {

    @NotBlank(message = "Refresh Token обязателен для заполнения")
    private String refreshToken;

    @NotBlank(message = "Выберите окружение")
    @Pattern(regexp = "test|production", message = "Окружение должно быть 'test' или 'production'")
    private String environment;

    private String tradeServerCode;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getTradeServerCode() {
        return tradeServerCode;
    }

    public void setTradeServerCode(String tradeServerCode) {
        this.tradeServerCode = tradeServerCode;
    }
}

